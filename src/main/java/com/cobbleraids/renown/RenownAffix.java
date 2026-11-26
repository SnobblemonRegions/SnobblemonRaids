package com.cobbleraids.renown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The one extra power a renowned boss fights with, and keeps when it is caught.
 *
 * <p>Each renown type (the boon its epithet grants) has its own five. The boss rolls one when its title
 * is rolled, it is stored on the Pokemon by id ({@link #id()}), and Showdown runs it from
 * {@code renown-affixes.js}, where every id here has a matching entry. Strength lives there and in the
 * config, never in the stored value, so retuning an affix changes it for every Pokemon that has it.
 *
 * <p>Free of Minecraft types so the roll and the lookup are unit-testable.
 */
public enum RenownAffix {
    // Attack
    BLOODRUSH(Group.ATTACK, "Bloodrush", "The first time it knocks out a foe in a battle: +1 Attack and +1 Speed."),
    OPENING_STRIKE(Group.ATTACK, "Opening Strike", "The first physical move it uses in a battle gets +1 priority."),
    SUNDERING_BLOWS(Group.ATTACK, "Sundering Blows", "Contact moves have a 20% chance to lower the target's Defence by 1."),
    VENDETTA(Group.ATTACK, "Vendetta", "+1 crit stage on moves against the Pokemon that last damaged it."),
    UNSTOPPABLE_FORCE(Group.ATTACK, "Unstoppable Force",
            "Its physical moves ignore the target's Defence boosts and go through Reflect, Aurora Veil and Substitute."),

    // Defence
    PLATED_HIDE(Group.DEFENCE, "Plated Hide", "Contact moves can't land critical hits on it."),
    BARBED_SHELL(Group.DEFENCE, "Barbed Shell", "A foe that hits it with a contact move loses 1/16 of its max HP."),
    IMMOVABLE(Group.DEFENCE, "Immovable", "It can't be forced out, and foes can't lower its Defence."),
    IRON_RIPOSTE(Group.DEFENCE, "Iron Riposte", "When a physical move hits it, a 20% chance the attacker's Attack drops by 1."),
    BRACING(Group.DEFENCE, "Bracing", "When it moves last in a turn: +1 Defence, at most +2 per battle."),

    // Sp. Atk
    DREAD_AURA(Group.SPECIAL_ATTACK, "Dread Aura", "On entering battle, foes' Sp. Def drops by 1."),
    AFTERBURN(Group.SPECIAL_ATTACK, "Afterburn", "Its special moves' secondary-effect chances are doubled."),
    SPELLPIERCE(Group.SPECIAL_ATTACK, "Spellpierce", "Its special moves go through Light Screen, Aurora Veil and Substitute."),
    ECHO_CAST(Group.SPECIAL_ATTACK, "Echo Cast", "Using the same special move two turns running gives the second use +1 crit stage."),
    STEADY_CHANNEL(Group.SPECIAL_ATTACK, "Steady Channel", "Special moves with under 100 accuracy get +10 accuracy."),

    // Sp. Def
    SPELLWARD(Group.SPECIAL_DEFENCE, "Spellward", "Special moves' secondary effects don't apply to it."),
    HOLLOW_MIND(Group.SPECIAL_DEFENCE, "Hollow Mind", "It can't be confused, Taunted, Encored or Tormented."),
    RESONANT_WARD(Group.SPECIAL_DEFENCE, "Resonant Ward", "When a special move hits it, a 30% chance of +1 Sp. Def, at most +2 per battle."),
    CLEANSING_VEIL(Group.SPECIAL_DEFENCE, "Cleansing Veil", "At the end of each turn, a 1-in-3 chance to cure its own status condition."),
    MIRRORGLASS(Group.SPECIAL_DEFENCE, "Mirrorglass", "The first status move aimed at it in a battle bounces back to the user."),

    // Speed
    QUICKDRAW(Group.SPEED, "Quickdraw", "Its first move after entering battle goes first among moves of the same priority."),
    SLIPSTREAM(Group.SPEED, "Slipstream", "Foes can't lower its Speed, and paralysis doesn't slow it."),
    OUTPACE(Group.SPEED, "Outpace", "+1 crit stage when it moves before its target."),
    FERAL_INSTINCT(Group.SPEED, "Feral Instinct", "The first time a foe raises a stat: +1 Speed. Once per battle."),
    HEAD_START(Group.SPEED, "Head Start", "The first time it enters a battle: +1 Speed."),

    // HP pool
    LAST_STAND(Group.HP_POOL, "Last Stand", "Once per battle, a hit that would knock it out leaves it at 1 HP."),
    SECOND_WIND(Group.HP_POOL, "Second Wind", "The first time it drops below 1/3 HP, it heals 1/4 of its max HP."),
    GRIT(Group.HP_POOL, "Grit", "The first super-effective hit it takes: +1 Defence and +1 Sp. Def."),
    UNBROKEN(Group.HP_POOL, "Unbroken", "Foes can't lower its stats while it has more than half its HP."),
    REGROWTH(Group.HP_POOL, "Regrowth", "Heals 1/16 of its max HP at the end of each turn.");

    /** The renown types; one per boon a datapack epithet can grant. */
    public enum Group { ATTACK, DEFENCE, SPECIAL_ATTACK, SPECIAL_DEFENCE, SPEED, HP_POOL }

    /** Longest id; the read path refuses anything longer before looking it up. */
    public static final int MAX_ID_LENGTH = 24;

    private static final Map<Group, List<RenownAffix>> BY_GROUP = new EnumMap<>(Group.class);
    static {
        for (Group group : Group.values()) BY_GROUP.put(group, new ArrayList<>());
        for (RenownAffix affix : values()) BY_GROUP.get(affix.group).add(affix);
        BY_GROUP.replaceAll((group, list) -> Collections.unmodifiableList(list));
    }

    private final Group group;
    private final String displayName;
    private final String description;

    RenownAffix(Group group, String displayName, String description) {
        this.group = group;
        this.displayName = displayName;
        this.description = description;
    }

    public Group group() { return group; }
    public String displayName() { return displayName; }
    /** What it does on a caught Pokemon, in one line, for tooltips and chat. */
    public String description() { return description; }

    /** The stored and wire form: "last_stand". Also the key in renown-affixes.js. */
    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** Empty for anything that is not exactly a known id: stored data and tags are untrusted. */
    public static Optional<RenownAffix> byId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) return Optional.empty();
        for (RenownAffix affix : values()) if (affix.id().equals(id)) return Optional.of(affix);
        return Optional.empty();
    }

    /** The renown type a boon belongs to; empty for a title-only boon, which rolls no affix. */
    public static Optional<Group> groupFor(RenownBoon boon) {
        if (boon == null) return Optional.empty();
        return switch (boon.kind()) {
            case NONE -> Optional.empty();
            case HP_POOL -> Optional.of(Group.HP_POOL);
            case STAT_FOCUS -> switch (boon.stat()) {
                case "attack" -> Optional.of(Group.ATTACK);
                case "defence" -> Optional.of(Group.DEFENCE);
                case "special_attack" -> Optional.of(Group.SPECIAL_ATTACK);
                case "special_defence" -> Optional.of(Group.SPECIAL_DEFENCE);
                case "speed" -> Optional.of(Group.SPEED);
                default -> Optional.empty();
            };
        };
    }

    public static List<RenownAffix> forGroup(Group group) {
        return BY_GROUP.get(group);
    }

    /** One affix for a boss with this boon, every one of its group equally likely; empty for a title-only boon. */
    public static Optional<RenownAffix> roll(RenownBoon boon, RandomGenerator random) {
        return groupFor(boon).map(group -> {
            List<RenownAffix> pool = BY_GROUP.get(group);
            return pool.get(random.nextInt(pool.size()));
        });
    }
}
