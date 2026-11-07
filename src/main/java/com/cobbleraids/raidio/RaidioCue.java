package com.cobbleraids.raidio;

/**
 * One sound to play to the listener. {@code note} is a note-block note, 0 to 24, which the sound layer turns into the vanilla pitch; unpitched voices use it
 * only for variety. {@code volume} is 0 (exclusive) to 1.
 */
public record RaidioCue(RaidioVoice voice, int note, float volume) {}
