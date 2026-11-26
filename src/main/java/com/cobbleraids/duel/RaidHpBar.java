package com.cobbleraids.duel;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.joml.Vector3f;

/**
 * The HP bar over a duel raid's boss: the shared pool, not any one player's copy, with "⚔ 3 dueling · 7:42" above it. Server-side
 * text displays riding the boss through Polymer, sent to everyone who can see the boss; no client mod. Layout and colours come
 * from {@link RaidHpBarLayout}, the trailing strip from {@link RaidHpBarTrail}. Server thread only.
 */
final class RaidHpBar {
    /** How long the fill takes to slide to a new value. */
    private static final int FILL_TICKS = 3;
    /** Layers sit a hair apart, nearest the viewer last, so they never fight for the same depth. */
    private static final float LAYER = 0.002f;

    private final PokemonEntity boss;
    private final ElementHolder holder = new ElementHolder();
    private final EntityAttachment attachment;
    private final TextDisplayElement track = square(RaidHpBarLayout.TRACK_COLOR);
    private final TextDisplayElement empty = square(RaidHpBarLayout.EMPTY_COLOR);
    private final TextDisplayElement trail = square(RaidHpBarLayout.TRAIL_COLOR);
    private final TextDisplayElement fill = square(RaidHpBarLayout.HIGH_COLOR);
    private final TextDisplayElement label = new TextDisplayElement(Component.empty());
    private final RaidHpBarTrail trailState = new RaidHpBarTrail(1f);
    private float height = Float.NaN;
    private float shownFill = -1f;
    private String shownText = "";

    private RaidHpBar(PokemonEntity boss) {
        this.boss = boss;
        trail.setInterpolationDuration(RaidHpBarTrail.SLIDE_TICKS);
        fill.setInterpolationDuration(FILL_TICKS);
        label.setBillboardMode(Display.BillboardConstraints.CENTER);
        label.setBrightness(Brightness.FULL_BRIGHT);
        label.setBackground(RaidHpBarLayout.TEXT_BACKGROUND);
        label.setShadow(true);
        label.setScale(new Vector3f(RaidHpBarLayout.TEXT_SCALE));
        holder.addElement(track);
        holder.addElement(empty);
        holder.addElement(trail);
        holder.addElement(fill);
        holder.addElement(label);
        // Ticking: the boss's own tick sends the holder's changes, so the bar costs nothing while nothing changes.
        this.attachment = EntityAttachment.ofTicking(holder, boss);
    }

    static RaidHpBar attach(PokemonEntity boss) {
        return new RaidHpBar(boss);
    }

    /** A one-pixel square of {@code color}: an empty text display draws only its background (see {@link RaidHpBarLayout}). */
    private static TextDisplayElement square(int color) {
        TextDisplayElement element = new TextDisplayElement(Component.empty());
        element.setBackground(color);
        element.setBillboardMode(Display.BillboardConstraints.CENTER);
        element.setBrightness(Brightness.FULL_BRIGHT);
        return element;
    }

    void update(float current, float max, int fighters, long secondsLeft, long tick) {
        float bossHeight = boss.getBbHeight();
        if (bossHeight != height) {
            height = bossHeight;
            place();
        }
        float fraction = RaidHpBarLayout.fraction(current, max);
        if (fraction != shownFill) {
            shownFill = fraction;
            fill.setBackground(RaidHpBarLayout.fillColor(fraction));
            fill.setScale(new Vector3f(RaidHpBarLayout.fillScaleX(fraction), RaidHpBarLayout.barScaleY(), 1f));
            fill.startInterpolationIfDirty();
        }
        if (trailState.update(fraction, tick)) {
            trail.setScale(new Vector3f(RaidHpBarLayout.fillScaleX(trailState.shown()), RaidHpBarLayout.barScaleY(), 1f));
            trail.startInterpolationIfDirty();
        }
        String status = RaidHpBarLayout.status(fighters, secondsLeft);
        long shownCurrent = RaidHpBarLayout.shownHp(current);
        long shownMax = RaidHpBarLayout.shownHp(max);
        String key = status + '|' + shownCurrent + '|' + shownMax;
        if (!key.equals(shownText)) {
            shownText = key;
            label.setText(Component.empty()
                    .append(Component.literal(status).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("\n"))
                    .append(Component.literal(String.format(java.util.Locale.ROOT, "%,d", shownCurrent)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.format(java.util.Locale.ROOT, " / %,d", shownMax)).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Lays every layer out for the boss's current height; the squares' origin is their bottom-left corner. */
    private void place() {
        float left = RaidHpBarLayout.barLeft();
        float bottom = RaidHpBarLayout.barBottom(height);
        float b = RaidHpBarLayout.BORDER;
        track.setTranslation(new Vector3f(left - b, bottom - b, 0f));
        track.setScale(new Vector3f((RaidHpBarLayout.WIDTH + 2 * b) / RaidHpBarLayout.PIXEL,
                (RaidHpBarLayout.HEIGHT + 2 * b) / RaidHpBarLayout.PIXEL, 1f));
        empty.setTranslation(new Vector3f(left, bottom, LAYER));
        empty.setScale(new Vector3f(RaidHpBarLayout.fillScaleX(1f), RaidHpBarLayout.barScaleY(), 1f));
        trail.setTranslation(new Vector3f(left, bottom, 2 * LAYER));
        trail.setScale(new Vector3f(RaidHpBarLayout.fillScaleX(trailState.shown()), RaidHpBarLayout.barScaleY(), 1f));
        fill.setTranslation(new Vector3f(left, bottom, 3 * LAYER));
        label.setTranslation(new Vector3f(0f, RaidHpBarLayout.textBottom(height), 0f));
    }

    void remove() {
        attachment.destroy();
        holder.destroy();
    }
}
