package com.cobbleraids.worldboss;

import com.cobbleraids.RaidLog;
import com.cobbleraids.catching.RaidPlayerRecords;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Server-thread adapter for the fixed daily calendar. No delayed tasks or offline catch-up. */
final class WorldBossDailyService {
    private static WorldBossState state;
    private static Instant processStarted, previousTick;
    private static WorldBossTicker ticker;

    private WorldBossDailyService() {}

    static void started(MinecraftServer server, WorldBossState saved) {
        state = saved;
        processStarted = previousTick = Instant.now();
        ticker = new WorldBossTicker(System.nanoTime());
        if (state.isQuarantined()) return;
        long now = processStarted.toEpochMilli();
        var campaign = state.campaign();
        if (now < campaign.wallHighWaterMillis()) campaign = campaign.daily(false, now);
        for (var event : List.copyOf(campaign.occurrences().values())) {
            boolean orphan = event.status() == WorldBossCampaign.Status.PREPARING
                    && (state.campaignEvent() == null || !event.eventId().equals(state.bossId()) || !state.active());
            boolean missed = event.status() == WorldBossCampaign.Status.PLANNED && now > event.slot().preparationAt().toEpochMilli();
            if (orphan || missed) campaign = campaign.transition(event.key(), event.eventId(), WorldBossCampaign.Status.SKIPPED,
                    "OFFLINE_PREPARATION", Math.max(now, campaign.wallHighWaterMillis()));
        }
        if (!campaign.equals(state.campaign())) commit(server, campaign);
    }

    static void stopped() { state = null; processStarted = previousTick = null; ticker = null; }

    static void tick(MinecraftServer server) {
        if (state == null || state.isQuarantined() || previousTick == null) return;
        Instant now = Instant.now(), previous = previousTick;
        previousTick = now;
        long elapsed = ticker.sample(System.nanoTime());
        if (elapsed < 0 || now.isBefore(previous) || Math.abs((now.toEpochMilli() - previous.toEpochMilli()) - elapsed) > WorldBossSchedule.START_TOLERANCE.toMillis()
                || now.toEpochMilli() < state.campaign().wallHighWaterMillis()) {
            if (state.campaign().dailyEnabled()) {
                commit(server, state.campaign().daily(false, now.toEpochMilli()));
                RaidLog.error("World Boss daily launches paused after wall/monotonic clock disagreement");
            }
            return;
        }
        preparationCues(server, previous, now);
        var campaign = state.campaign();
        for (var pending : List.copyOf(campaign.occurrences().values())) {
            if (pending.status() == WorldBossCampaign.Status.PLANNED && now.isAfter(pending.slot().combatAt().plus(WorldBossSchedule.START_TOLERANCE))) {
                var expired = campaign.transition(pending.key(), pending.eventId(), WorldBossCampaign.Status.SKIPPED, "PREPARATION_MISSED", now.toEpochMilli());
                if (!commit(server, expired)) return;
                campaign = state.campaign();
            }
        }
        if (!WorldBossConfigManager.get().enabled() || !campaign.dailyEnabled() || campaign.active() == null) return;
        var slot = WorldBossSchedule.next(now);
        if (campaign.enabledAtMillis() > slot.preparationAt().toEpochMilli()) return;
        String key = WorldBossCampaign.key(campaign.activeSeason(), slot);
        var event = campaign.occurrences().get(key);
        boolean occupied = state.active() || WorldBossService.hasOwnedDuels() || campaign.occurrences().values().stream()
                .anyMatch(value -> value.status() == WorldBossCampaign.Status.PREPARING || value.status() == WorldBossCampaign.Status.OPEN);
        if (!occupied && crossed(previous, now, slot.combatAt().minusSeconds(600))) {
            var planned = campaign.plan(slot, now.toEpochMilli()).cue(key, WorldBossCampaign.TEN_MINUTE, now.toEpochMilli());
            if (planned != campaign && commit(server, planned)) announce(server, planned.require(key), "arrives in ten minutes. Gather your team.", false);
            campaign = state.campaign(); event = campaign.occurrences().get(key);
        }
        if (WorldBossSchedule.preparationDue(processStarted, previous, now, null) == null) return;
        if (event != null && event.status() != WorldBossCampaign.Status.PLANNED) return;
        campaign = campaign.plan(slot, now.toEpochMilli());
        event = campaign.require(key);
        String problem = occupied ? "EVENT_SLOT_OCCUPIED" : startProblem(server, campaign.active());
        if (problem != null) {
            commit(server, campaign.transition(key, event.eventId(), WorldBossCampaign.Status.SKIPPED, bounded(problem), now.toEpochMilli()));
            RaidLog.warn("World Boss occurrence {} skipped: {}", key, problem);
            return;
        }
        try { RaidPlayerRecords.verifySettlementStorage(server); }
        catch (Exception failure) {
            commit(server, campaign.transition(key, event.eventId(), WorldBossCampaign.Status.SKIPPED, "SETTLEMENT_UNAVAILABLE", now.toEpochMilli()));
            RaidLog.error("World Boss occurrence {} blocked by settlement storage", key, failure);
            return;
        }
        var reserved = campaign.transition(key, event.eventId(), WorldBossCampaign.Status.PREPARING, "", now.toEpochMilli())
                .cue(key, WorldBossCampaign.FIVE_MINUTE, now.toEpochMilli());
        if (!commit(server, reserved)) return;
        problem = WorldBossService.startScheduled(server, reserved.require(key));
        if (problem != null) {
            var failed = state.campaign().transition(key, event.eventId(), WorldBossCampaign.Status.SKIPPED, bounded(problem),
                    Math.max(System.currentTimeMillis(), state.campaign().wallHighWaterMillis()));
            commit(server, failed);
            RaidLog.warn("World Boss reserved occurrence {} could not start: {}", key, problem);
        }
    }

    private static void preparationCues(MinecraftServer server, Instant previous, Instant now) {
        var event = state.campaignEvent();
        if (!state.active() || state.closeReason() != null || event == null || state.clock() == null || state.clock().phase() != WorldBossClock.Phase.PREPARING) return;
        for (int cue : new int[]{WorldBossCampaign.ONE_MINUTE, WorldBossCampaign.ENTRANCE}) {
            Instant threshold = cue == WorldBossCampaign.ONE_MINUTE ? event.slot().combatAt().minusSeconds(60) : event.slot().entranceAt();
            boolean entranceRetry = cue == WorldBossCampaign.ENTRANCE && !now.isBefore(threshold) && !now.isAfter(threshold.plus(WorldBossSchedule.START_TOLERANCE));
            if ((event.cues() & cue) != 0 || !crossed(previous, now, threshold) && !entranceRetry) continue;
            var candidate = state.campaign().cue(event.key(), cue, now.toEpochMilli());
            if (cue == WorldBossCampaign.ENTRANCE) {
                var oldCampaign = state.campaign(); var oldScenes = state.scenes();
                state.campaign(candidate); state.recordScene(WorldBossSceneScript.Kind.ENTRANCE, threshold.toEpochMilli());
                if (WorldBossService.flushCampaign(server)) WorldBossScenes.dispatch(server, new WorldBossSceneLedger.Key(event.eventId(), WorldBossSceneScript.Kind.ENTRANCE));
                else { state.campaign(oldCampaign); state.scenes(oldScenes); }
            } else if (commit(server, candidate)) announce(server, event, "awakens in one minute. Ready players inside the bubble will enter automatically.", false);
        }
    }

    private static boolean crossed(Instant previous, Instant now, Instant threshold) {
        return WorldBossSchedule.crossed(processStarted, previous, now, threshold)
                && !now.isAfter(threshold.plus(WorldBossSchedule.START_TOLERANCE));
    }

    private static void announce(MinecraftServer server, WorldBossCampaign.Occurrence event, String text, boolean local) {
        Component message = Component.literal("[World Boss] " + event.displayName() + " " + text).withStyle(ChatFormatting.LIGHT_PURPLE);
        for (var player : server.getPlayerList().getPlayers()) {
            if (local && (!player.level().dimension().location().toString().equals(state.dimension())
                    || !WorldBossZone.inside(state.x(), state.y(), state.z(), WorldBossRules.zoneRadius(state, WorldBossConfigManager.get()),
                            player.getX(), player.getY(), player.getZ()))) continue;
            player.sendSystemMessage(message);
        }
    }

    static boolean commit(MinecraftServer server, WorldBossCampaign candidate) {
        var old = state.campaign();
        state.campaign(candidate);
        if (WorldBossService.flushCampaign(server)) return true;
        state.campaign(old);
        return false;
    }

    static String stage(MinecraftServer server, String id, String name) {
        String problem = controlProblem(server);
        if (problem != null) return problem;
        var config = WorldBossConfigManager.get();
        if (config.raidDefinitions().size() != 1) return "configure exactly one raid definition before staging";
        try {
            var candidate = new WorldBossCampaign.Season(id, UUID.randomUUID(), name, config.raidDefinitions().getFirst(), 0);
            return commit(server, state.campaign().stage(candidate, System.currentTimeMillis())) ? null : "season stage could not be saved";
        } catch (RuntimeException invalid) { return invalid.getMessage(); }
    }

    static String activate(MinecraftServer server) {
        String problem = controlProblem(server);
        if (problem != null) return problem;
        if (state.active() || WorldBossService.hasOwnedDuels()) return "an active/held encounter or its duels still reserve the event slot";
        if (state.campaign().staged() == null) return "stage a new season first";
        problem = startProblem(server, state.campaign().staged());
        if (problem != null) return problem;
        try {
            RaidPlayerRecords.verifySettlementStorage(server);
            return commit(server, state.campaign().activate(System.currentTimeMillis())) ? null : "season activation could not be saved";
        } catch (Exception failure) { return "season activation blocked: " + failure.getMessage(); }
    }

    static String daily(MinecraftServer server, boolean enabled) {
        String problem = controlProblem(server);
        if (problem != null) return problem;
        if (enabled) {
            if (state.campaign().active() == null) return "activate a staged season first";
            problem = startProblem(server, state.campaign().active());
            if (problem != null) return problem;
        }
        try {
            if (enabled) RaidPlayerRecords.verifySettlementStorage(server);
            return commit(server, state.campaign().daily(enabled, System.currentTimeMillis())) ? null : "daily scheduling change could not be saved";
        } catch (Exception failure) { return "daily scheduling change blocked: " + failure.getMessage(); }
    }

    static List<String> status() {
        if (state == null) return List.of("Season service has not started.");
        var campaign = state.campaign();
        var owner = campaign.active();
        var lines = new java.util.ArrayList<String>(List.of(owner == null ? "No active season." : "Season " + owner.id() + ": " + owner.name() + ", next awakening " + owner.awakening() + ", natural victories " + owner.victories(),
                campaign.staged() == null ? "No staged season." : "Staged: " + campaign.staged().id() + " / " + campaign.staged().name(),
                "Daily launches: " + campaign.dailyEnabled() + " (12:00 and 20:00 America/New_York)",
                "Archived/active seasons: " + campaign.seasons().size() + ", occurrence receipts: " + campaign.occurrences().size()));
        campaign.occurrences().values().stream().filter(value -> !value.terminal()).forEach(value -> lines.add("Reserved: " + value.key() + " / " + value.status()));
        return lines;
    }

    private static String startProblem(MinecraftServer server, WorldBossCampaign.Season season) {
        var config = WorldBossConfigManager.get();
        if (!config.enabled()) return "World Boss configuration is disabled";
        if (config.raidDefinitions().size() != 1 || !config.raidDefinitions().getFirst().equals(season.definition())) return "the selected definition no longer matches this season";
        return WorldBossService.livePreflight(server, config);
    }
    private static String controlProblem(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Season control requires the server thread");
        return state == null || state.isQuarantined() ? "World Boss saved data is unavailable or quarantined" : null;
    }
    private static String bounded(String text) { return text.substring(0, Math.min(160, text.length())).replaceAll("[\\p{Cntrl}\u00a7]", " "); }
}
