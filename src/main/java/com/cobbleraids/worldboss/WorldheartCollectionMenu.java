package com.cobbleraids.worldboss;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** Account collection display. Every icon is a menu projection; none is an ownership-bearing item. */
final class WorldheartCollectionMenu extends ChestMenu {
    private static final int PAGE_SIZE = 9;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm z", Locale.US).withZone(WorldBossSchedule.ZONE);
    private final ServerPlayer owner;
    private final SimpleContainer display;
    private List<WorldBossCampaign.Season> shown = List.of();
    private int page, ticks;
    private UUID attuningCharacter;
    private boolean sceneSettings;
    private List<UUID> partyChoices = List.of();
    static String open(ServerPlayer player) {
        if (WorldBossService.collectionState() == null) return "Worldheart collection is unavailable while saved data needs repair.";
        if (com.cobblemon.mod.common.Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) return "Open your collection between battles.";
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new WorldheartCollectionMenu(id, player, new SimpleContainer(27)), Component.literal("Worldheart Collection")));
        return null;
    }
    private WorldheartCollectionMenu(int id, ServerPlayer owner, SimpleContainer display) {
        super(MenuType.GENERIC_9x3, id, owner.getInventory(), display, 3); this.owner = owner; this.display = display; refresh();
    }
    private void refresh() {
        var state = WorldBossService.collectionState(); if (state == null) return;
        if (sceneSettings) { refreshSceneSettings(state); return; }
        if (attuningCharacter != null) { refreshParty(state); return; }
        var campaign = state.campaign(); var collection = state.collectionView(); UUID player = owner.getUUID();
        var seasons = campaign.seasons().values().stream().sorted(Comparator.comparing((WorldBossCampaign.Season season) -> !season.id().equals(campaign.activeSeason())).thenComparing(WorldBossCampaign.Season::id)).toList();
        page = Math.max(0, Math.min(page, Math.max(0, (seasons.size() - 1) / PAGE_SIZE)));
        shown = seasons.subList(Math.min(page * PAGE_SIZE, seasons.size()), Math.min((page + 1) * PAGE_SIZE, seasons.size()));
        for (int slot = 0; slot < 27; slot++) display.setItem(slot, icon(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY, List.of()));
        display.setItem(4, icon(Items.BOOK, "Worldhearts: earned, account-bound, permanent", ChatFormatting.GOLD,
                List.of("Qualify for natural victories to gain mastery.", "Base guaranteed at 12; early chance before it.", "Styles at 24 and 40. No battle-stat bonuses.", "Choose one archived project alongside current progress.")));
        if (seasons.isEmpty()) display.setItem(13, icon(Items.CLOCK, "The first boss legacy is yet to begin", ChatFormatting.AQUA, List.of("Your collection will appear as seasons open.")));
        for (int index = 0; index < shown.size(); index++) {
            var season = shown.get(index); var progress = collection.progress(player, season.characterId()); boolean current = season.id().equals(campaign.activeSeason());
            boolean selected = season.characterId().equals(collection.selectedLegacy(player));
            List<String> lore = new ArrayList<>(); lore.add(current ? "Current boss: mastery progresses automatically" : selected ? "Selected Legacy Attunement" : "Archived: click to select this mastery project");
            lore.add("Season: " + season.id());
            if (progress.owned()) lore.add(season.definition().equals("cobbleraids:giratina") ? "Right-click to attune a Pokemon" : "Appearance not available for this Worldheart yet");
            lore.add("Qualified mastery steps: " + progress.steps());
            if (progress.owned()) lore.add("Owned - " + switch (progress.style()) { case 1 -> "Style I unlocked"; case 2 -> "Style II unlocked"; default -> "Base Worldheart unlocked"; });
            else lore.add("Guaranteed base: " + progress.steps() + "/12");
            if (progress.nextMilestone() > 0 && progress.owned()) lore.add("Next style milestone: " + progress.nextMilestone());
            if (progress.unlock() != null) {
                var unlock = progress.unlock();
                campaign.occurrences().values().stream().filter(event -> event.eventId().equals(unlock.event())).findFirst().ifPresent(event -> lore.add("Earned from: " + event.displayName()));
                lore.add("Earned: " + DATE.format(Instant.ofEpochMilli(unlock.atMillis()))); lore.add("Earning trainer: " + owner.getGameProfile().getName());
                lore.add(unlock.legacy() ? "Unlocked through archived mastery" : unlock.early() ? "Early awakening" : "Guaranteed mastery unlock");
            }
            display.setItem(9 + index, icon(progress.owned() ? Items.NETHER_STAR : selected ? Items.ENDER_EYE : Items.AMETHYST_SHARD,
                    season.name() + "'s Worldheart", selected ? ChatFormatting.GREEN : current ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE, lore));
        }
        display.setItem(18, icon(Items.ARROW, "Previous page", ChatFormatting.YELLOW, List.of()));
        display.setItem(20, icon(Items.NOTE_BLOCK, "Boss scene settings", ChatFormatting.LIGHT_PURPLE, List.of("Voice level, subtitles, reduced effects and skip.")));
        display.setItem(21, icon(Items.BARRIER, "Clear legacy project", ChatFormatting.RED, List.of("Earned progress stays in your collection.")));
        long blessing = Math.max(0, (collection.blessingUntil() - System.currentTimeMillis() + 59_999) / 60_000);
        display.setItem(23, icon(Items.AMETHYST_SHARD, "Worldheart effects: " + state.benefitsView().effects(player).name(), ChatFormatting.AQUA, List.of("Click: Full > Minimal > Off", "Your view of all Worldheart effects.")));
        display.setItem(24, icon(Items.SUNFLOWER, blessing > 0 ? "Community blessing: " + blessing + " minutes" : "Community blessing inactive", ChatFormatting.GOLD, List.of("+10% of base RP on ordinary raid victories", "World boss, shop and claim multipliers are excluded.")));
        display.setItem(22, icon(Items.PAPER, "Page " + (page + 1) + "/" + Math.max(1, (seasons.size() + PAGE_SIZE - 1) / PAGE_SIZE), ChatFormatting.WHITE, List.of()));
        display.setItem(25, icon(Items.BARRIER, "Close", ChatFormatting.RED, List.of()));
        display.setItem(26, icon(Items.ARROW, "Next page", ChatFormatting.YELLOW, List.of()));
    }
    private void refreshSceneSettings(WorldBossState state) {
        for (int slot = 0; slot < 27; slot++) display.setItem(slot, icon(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY, List.of()));
        var options = state.benefitsView().sceneOptions(owner.getUUID());
        display.setItem(4, icon(Items.BOOK, "Your World Boss scene preferences", ChatFormatting.LIGHT_PURPLE, List.of("Optional scenes never control battle or rewards.", "Your camera remains under your control.")));
        display.setItem(10, icon(Items.REDSTONE, "Voice quieter", ChatFormatting.AQUA, List.of("Current level: " + options.voiceVolume() + "%")));
        display.setItem(11, icon(Items.NOTE_BLOCK, options.voiceVolume() == 0 ? "Voice muted" : "Mute scene voice", ChatFormatting.AQUA, List.of("Click to toggle mute / 70%.")));
        display.setItem(12, icon(Items.GLOWSTONE_DUST, "Voice louder", ChatFormatting.AQUA, List.of("Changing volume stops current voice; new level applies next scene.")));
        display.setItem(14, icon(Items.PAPER, "Subtitles: " + (options.subtitles() ? "On" : "Off"), ChatFormatting.YELLOW, List.of("Click to toggle optional dialogue subtitles.")));
        display.setItem(16, icon(Items.ENDER_PEARL, "Scene effects: " + options.effects(), ChatFormatting.AQUA, List.of("Click: Full > Minimal > Off")));
        display.setItem(18, icon(Items.ARROW, "Back to collection", ChatFormatting.YELLOW, List.of()));
        display.setItem(21, icon(Items.BARRIER, "Skip current scene", ChatFormatting.RED, List.of("Rewards and combat timing stay intact.")));
        display.setItem(25, icon(Items.BARRIER, "Close", ChatFormatting.RED, List.of()));
    }
    private void refreshParty(WorldBossState state) {
        for (int slot = 0; slot < 27; slot++) display.setItem(slot, icon(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY, List.of()));
        var choices = new ArrayList<UUID>();
        display.setItem(4, icon(Items.NETHER_STAR, "Choose your Worldheart companion", ChatFormatting.LIGHT_PURPLE,
                List.of("One Pokemon at a time; native form and shiny stay visible.", "Trade, evolution or moving it to PC detaches safely.", "Lucario, Charizard, Gengar, Eevee, Gardevoir, Dragonite.")));
        for (var pokemon : com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(owner)) {
            var species = pokemon.getSpecies().getResourceIdentifier().toString(); boolean supported = WorldheartEffects.supported(species, WorldheartEffects.APPEARANCE);
            choices.add(pokemon.getUuid()); display.setItem(9 + choices.size() - 1, icon(supported ? Items.ENDER_PEARL : Items.GRAY_DYE,
                    pokemon.getSpecies().getName(), supported ? ChatFormatting.GREEN : ChatFormatting.GRAY, List.of(supported ? "Click to attune your highest unlocked style" : "Appearance unavailable for this species")));
        }
        partyChoices = List.copyOf(choices);
        display.setItem(18, icon(Items.ARROW, "Back to collection", ChatFormatting.YELLOW, List.of()));
        display.setItem(21, icon(Items.BARRIER, "Detach Worldheart", ChatFormatting.RED, List.of("Your collection and Pokemon stay intact.")));
        display.setItem(25, icon(Items.BARRIER, "Close", ChatFormatting.RED, List.of()));
    }
    private static ItemStack icon(Item item, String title, ChatFormatting color, List<String> lore) {
        var stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(color).withStyle(style -> style.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lore.stream().map(line -> (Component) Component.literal(line).withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false))).toList())); return stack;
    }
    @Override public boolean stillValid(Player player) { return player == owner && owner.isAlive() && WorldBossService.collectionState() != null
            && com.cobblemon.mod.common.Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(owner) == null; }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || type != ClickType.PICKUP || (button != 0 && button != 1)) return;
        if (slot == 25) { owner.closeContainer(); return; }
        if (sceneSettings) {
            if (slot == 18) { sceneSettings = false; refresh(); return; }
            if (slot == 21) { WorldBossScenes.skip(owner); return; }
            var state = WorldBossService.collectionState(); if (state == null) return;
            var options = state.benefitsView().sceneOptions(owner.getUUID()); String problem;
            if (slot == 10) problem = WorldBossScenes.options(owner, Math.max(0, options.voiceVolume() - 10), null, null);
            else if (slot == 11) problem = WorldBossScenes.options(owner, options.voiceVolume() == 0 ? 70 : 0, null, null);
            else if (slot == 12) problem = WorldBossScenes.options(owner, Math.min(100, options.voiceVolume() + 10), null, null);
            else if (slot == 14) problem = WorldBossScenes.options(owner, null, !options.subtitles(), null);
            else if (slot == 16) problem = WorldBossScenes.options(owner, null, null, WorldBossBenefits.Effects.values()[(options.effects().ordinal() + 1) % WorldBossBenefits.Effects.values().length]);
            else return;
            if (problem != null) owner.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED)); refresh(); return;
        }
        if (slot == 20 && attuningCharacter == null) { sceneSettings = true; refresh(); return; }
        if (attuningCharacter != null) {
            if (slot == 18) { attuningCharacter = null; refresh(); return; }
            String problem;
            if (slot == 21) problem = WorldheartPresentation.detach(owner);
            else { int index = slot - 9; if (index < 0 || index >= partyChoices.size()) return; problem = WorldheartPresentation.attune(owner, attuningCharacter, partyChoices.get(index), -1); }
            owner.sendSystemMessage(Component.literal(problem == null ? "Worldheart attunement saved." : problem).withStyle(problem == null ? ChatFormatting.GREEN : ChatFormatting.RED)); refresh(); return;
        }
        if (slot == 18) { page = Math.max(0, page - 1); refresh(); return; }
        if (slot == 26) { page++; refresh(); return; }
        var state = WorldBossService.collectionState(); if (state == null) return;
        if (slot == 23) {
            var mode = state.benefitsView().effects(owner.getUUID()); var next = WorldBossBenefits.Effects.values()[(mode.ordinal() + 1) % WorldBossBenefits.Effects.values().length];
            String problem = WorldheartPresentation.effects(owner, next.name()); if (problem != null) owner.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED)); refresh(); return;
        }
        UUID character;
        if (slot == 21) character = null;
        else {
            int index = slot - 9; if (index < 0 || index >= shown.size()) return;
            var season = shown.get(index);
            if (button == 1 && state.collectionView().progress(owner.getUUID(), season.characterId()).owned()) {
                if (!season.definition().equals("cobbleraids:giratina")) { owner.sendSystemMessage(Component.literal("This Worldheart has no available appearance yet.").withStyle(ChatFormatting.AQUA)); return; }
                attuningCharacter = season.characterId(); refresh(); return;
            }
            if (season.id().equals(state.campaign().activeSeason())) { owner.sendSystemMessage(Component.literal("Your current boss mastery progresses automatically.").withStyle(ChatFormatting.AQUA)); return; }
            character = season.characterId();
        }
        String problem = WorldBossService.selectLegacy(owner.server, owner.getUUID(), character);
        owner.sendSystemMessage(Component.literal(problem == null ? character == null ? "Legacy project cleared. Your progress is preserved." : "Legacy project saved. Future qualified victories also advance it." : problem).withStyle(problem == null ? ChatFormatting.GREEN : ChatFormatting.RED));
        refresh();
    }
    @Override public void broadcastChanges() { if (++ticks >= 20) { ticks = 0; refresh(); } super.broadcastChanges(); }
    static void announce(ServerPlayer player, WorldBossState state) {
        var collection = state.collectionView(); var grant = collection.victory(state.bossId());
        if (grant == null || !grant.awards().containsKey(player.getUUID())) return;
        var progress = collection.progress(player.getUUID(), grant.character());
        player.sendSystemMessage(Component.literal(milestone(progress, grant.event()) + "Worldheart mastery: " + progress.steps() + (progress.owned() ? " - Worldheart owned." : "/12 toward the guaranteed Worldheart.") + " [Collection]")
                .withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cr worldboss collection"))));
        var award = grant.awards().get(player.getUUID());
        if (award.legacyCharacter() != null) {
            var legacy = collection.progress(player.getUUID(), award.legacyCharacter());
            String name = state.campaign().seasons().values().stream().filter(season -> season.characterId().equals(award.legacyCharacter())).map(WorldBossCampaign.Season::name).findFirst().orElse("Archived boss");
            player.sendSystemMessage(Component.literal(milestone(legacy, grant.event()) + name + " legacy mastery: " + legacy.steps() + (legacy.owned() ? " - Worldheart owned." : "/12.")).withStyle(ChatFormatting.GREEN));
        }
    }
    private static String milestone(WorldheartCollection.Progress progress, UUID event) {
        if (progress.unlock() != null && progress.unlock().event().equals(event)) return "Your Worldheart has awakened! ";
        return progress.steps() == WorldheartCollection.STYLE_ONE ? "Worldheart Style I unlocked! " : progress.steps() == WorldheartCollection.STYLE_TWO ? "Worldheart Style II unlocked! " : "";
    }
}
