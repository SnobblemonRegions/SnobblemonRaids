package com.cobbleraids.mixin.battle;

import com.cobbleraids.perf.RaidPerf;
import com.cobbleraids.fault.RaidFaultBarrier;
import com.cobbleraids.api.encounter.EncounterRules;
import com.cobbleraids.battle.RaidBannedGimmicks;
import com.cobbleraids.battle.RaidBannedMoves;
import com.cobbleraids.battle.RaidSwitchRules;
import com.cobbleraids.lifecycle.RaidLifecycleCoordinator;
import com.cobbleraids.raid.RaidRegistry;
import com.cobbleraids.raid.RaidSession;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.BagItemActionResponse;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.FleeAttemptActionResponse;
import com.cobblemon.mod.common.battles.ForfeitActionResponse;
import com.cobblemon.mod.common.battles.HealItemActionResponse;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionRequest;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket;
import com.cobblemon.mod.common.net.messages.client.battle.BattleQueueRequestPacket;
import com.cobblemon.mod.common.net.messages.server.battle.BattleSelectActionsPacket;
import com.cobblemon.mod.common.net.serverhandling.battle.BattleSelectActionsHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raid-specific action boundary fixes.
 *
 * 1) A move sent without a target is filled in RaidMoveActionResponseMixin, not here (see there).
 *
 * 2) Explicit forfeits never reach stock PokemonBattle.checkForfeit(); raid withdrawal semantics
 *    are handled by RaidLifecycleCoordinator instead.
 *
 * 2b) "No switching" refuses a chosen switch, never the forced replacement of a fainted Pokemon (RaidSwitchRules).
 *
 * 3) A RaidBannedMoves entry is rejected before it reaches Showdown, since a faint caused outside
 *    the raid's -raiddamage/-raidheal pipeline (e.g. Perish Song fainting every active Pokemon at
 *    once) satisfies no RaidLifecycleCoordinator victory/defeat path and hangs the battle UI. The
 *    boss's own move choice is guarded separately by RaidBossBattleAI, since it never goes through
 *    this packet.
 */
@Mixin(BattleSelectActionsHandler.class)
public abstract class RaidBattleSelectActionsMixin {
    @Inject(
        method = "handle(Lcom/cobblemon/mod/common/net/messages/server/battle/BattleSelectActionsPacket;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cobbleRaids$prepareRaidActions(BattleSelectActionsPacket packet, MinecraftServer server,
                                                 ServerPlayer player, CallbackInfo ci) {
        long started = RaidPerf.start();
        try {
            cobbleRaids$prepareRaidActionsOrThrow(packet, server, player, ci);
        } catch (Exception ex) {
            // Fails open: the packet falls through to Cobblemon's stock handler. A banned move or a
            // forfeit might slip past for one action, which the lifecycle's own victory/defeat paths
            // still have to cope with anyway. Throwing instead would unwind into the network thread's
            // packet dispatch, which handles every battle on the server, not just this raid.
            RaidFaultBarrier.report("mixin:selectActions", ex);
        } finally {
            RaidPerf.stop("mixin:selectActions", started);
        }
    }

    private static void cobbleRaids$prepareRaidActionsOrThrow(BattleSelectActionsPacket packet,
                                                               MinecraftServer server,
                                                               ServerPlayer player, CallbackInfo ci) {
        PokemonBattle battle = BattleRegistry.getBattle(packet.getBattleId());
        RaidSession raid = RaidRegistry.get(battle);
        // Rules apply to every battle the registry knows is a raid, whatever its status. They used to
        // sit behind status == ACTIVE alongside the target fill, so the moment a raid was not ACTIVE
        // (or the fill had nothing to do) banned moves, switching and items all went unchecked.
        if (raid == null) return;

        // An owner's rules ride on top of CobbleRaids' own ban list, never instead of it: the
        // safety bans exist because those moves faint a Pokemon outside the damage pipeline the
        // shared health pool is driven by, so letting an owner switch one back on would not make a
        // harder fight, it would make a stuck one.
        EncounterRules rules = raid.rules();

        String refusal = null;
        for (ShowdownActionResponse response : packet.getShowdownActionResponses()) {
            if (response instanceof MoveActionResponse move) {
                String id = move.getMoveName();
                if (RaidBannedMoves.isBanned(id)) {
                    refusal = RaidBannedMoves.displayName(id) + " is banned in raid battles and cannot be used.";
                } else if (rules.bans(id)) {
                    refusal = RaidBannedMoves.displayName(id) + " is not allowed in this battle.";
                } else if (RaidBannedGimmicks.isBanned(move.getGimmickID())) {
                    refusal = "Z-Moves are banned in raid battles and cannot be used.";
                }
            } else if (response instanceof FleeAttemptActionResponse) {
                // The 1.8 client's Run button, shown because the boss actor is WILD. Cobblemon would
                // refuse it with its own generic message; this one says where the exit actually is.
                refusal = "You cannot run from a raid. Use /cobbleraids leave to leave it.";
            } else if (response instanceof SwitchActionResponse
                    && RaidSwitchRules.refusesSwitch(rules.switchingAllowed(), cobbleRaids$isForcedReplacement(battle, player))) {
                refusal = "Switching is not allowed in this battle.";
            } else if ((response instanceof BagItemActionResponse || response instanceof HealItemActionResponse)
                    && !rules.itemsAllowed()) {
                refusal = "Items are not allowed in this battle.";
            }
            if (refusal != null) break;
        }

        if (refusal != null) {
            // Cancelled and the request resent, which is what turns a refusal into "pick again"
            // rather than a client stuck waiting for a turn that will never resolve. Same path the
            // safety ban already used; the only thing that changed is what can trigger it.
            ci.cancel();
            player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
            BattleActor actor = battle.getActor(player);
            if (actor != null && actor.getRequest() != null) {
                actor.sendUpdate(new BattleQueueRequestPacket(actor.getRequest()));
                actor.sendUpdate(new BattleMakeChoicePacket());
            }
            return;
        }

        // A move sent without a target is filled in RaidMoveActionResponseMixin.isValid, where
        // Cobblemon validates it, so it no longer depends on anything this handler can see.

        boolean requestedForfeit = packet.getShowdownActionResponses().stream()
                .anyMatch(response -> response instanceof ForfeitActionResponse);
        // Withdrawal is a lifecycle step and only means something while the raid is being fought;
        // outside that, a forfeit takes Cobblemon's stock path exactly as before.
        if (!requestedForfeit || raid.getStatus() != RaidSession.Status.ACTIVE) return;

        ci.cancel();
        if (!raid.isActiveParticipant(player.getUUID())) return;

        if (!raid.isFleeAllowed()) {
            player.sendSystemMessage(Component.literal("Fleeing is disabled for this raid.").withStyle(ChatFormatting.RED));
            BattleActor actor = battle.getActor(player);
            if (actor != null && actor.getRequest() != null) {
                actor.sendUpdate(new BattleQueueRequestPacket(actor.getRequest()));
                actor.sendUpdate(new BattleMakeChoicePacket());
            }
            return;
        }

        RaidLifecycleCoordinator.withdrawPlayer(raid, player);
    }

    /** Whether the request this player is answering is Showdown asking them to replace a fainted Pokemon. */
    private static boolean cobbleRaids$isForcedReplacement(PokemonBattle battle, ServerPlayer player) {
        BattleActor actor = battle.getActor(player);
        ShowdownActionRequest request = actor == null ? null : actor.getRequest();
        return request != null && RaidSwitchRules.isForcedReplacement(request.getForceSwitch());
    }
}
