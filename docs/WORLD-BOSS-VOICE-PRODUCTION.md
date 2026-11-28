# World boss recorded-scene production

The scene engine is implemented with subtitles and quiet vanilla cue fallbacks. No recorded performances are supplied. The empty voice manifest is intentional; it is not a voiced-release signoff. No performer has been contacted and no commission, purchase or publication has occurred.

## Performance brief

The first character theme is Distortion. Use a controlled, ancient, telepathic English delivery. The adversary becomes unsettled by the trainers' cooperation; avoid a comic villain shout. Dialogue never speaks player names. The title carries the exact character name and awakening number, so reusable lines remain accurate beyond the bespoke chapters.

The authoritative text, subtitle offsets and script revisions are in [WorldBossSceneScript.java](../src/main/java/com/cobbleraids/worldboss/WorldBossSceneScript.java). Record these exact lines. An operator can obtain each script's exact hash and maximum recording length with `/cr worldboss scene catalogue` (permission level 2).

Once a recorded performance is used, its content hash is pinned in the scene receipt. Keep that version available; replacing its bytes under the same ID makes old playback use the text fallback. A chapter originally recorded as text-only may use its first approved performance later if the exact frozen script still matches. New performances or changed text should receive a deliberate new version, with earlier referenced versions retained.

| Script ID | Dialogue | Maximum recording |
| --- | --- | --- |
| `distortion_entrance_first_v1` | You built your sanctuary upon my prison. / Did you believe I would never wake? / Step forward. Let your bonds face the dark. | 13 seconds |
| `distortion_entrance_third_v1` | Twice you have sealed this gate. / Twice you have mistaken survival for victory. / Show me what your sanctuary is worth. | 13 seconds |
| `distortion_entrance_return_v1` | Your sanctuary endures. So do I. / Gather your courage. Stand beside one another. / Let this place remember our battle. | 13 seconds |
| `distortion_victory_first_v1` | Remember this victory, little trainers. / I will remember every wound. / I will return stronger. | 10 seconds |
| `distortion_victory_respect_v1` | That bond... I had forgotten its strength. / Guard this sanctuary. I will return stronger. | 9 seconds |
| `distortion_escape_v1` | Your wards are failing. / Next time, bring more than courage. | 7 seconds |

The generic entrance, victory and escape scripts use the reusable return, respectful victory and escape text respectively. Their IDs are `entrance_generic_v1`, `victory_generic_v1`, and `escape_generic_v1`. They need their own explicit manifest entries if recorded. The third-awakening line is used only for awakening III; other later appearances never claim exactly two earlier seals.

Place phrases at the authored subtitle offsets. Preserve a one-second tail between the maximum recording length and the scene boundary. Leave room for a natural performance rather than speeding audio to force it into the budget. A script edit requires a deliberate new version; retain versions referenced by scene history.

## Recording and packaging

Use recorded performances with documented permission for server/resource-pack redistribution. Retain the performer agreement and rights evidence with the release records. The manifest's approval flag records that review; it cannot establish licensing rights by itself. There is no generated runtime speech.

Deliver a single Ogg/Vorbis audio stream, mono or stereo, 22.05-48 kHz, no clipping, maximum 4 MiB/file. Human review must verify wording, timing, intelligibility, loudness and performance quality against subtitles. The technical decoder check does not replace listening.

Place each file under `src/main/resources/worldboss_voice/worldboss/v1/`, and add an entry to [manifest.json](../src/main/resources/worldboss_voice/manifest.json):

```json
{
  "script": "distortion_entrance_first_v1",
  "script_sha256": "<exact value from the catalogue command>",
  "file": "worldboss/v1/distortion_entrance_first_v1.ogg",
  "sha256": "<lowercase SHA-256 of the audio file>",
  "duration_ms": 12000,
  "performer": "<credited performer>",
  "license": "<agreement/evidence identifier granting redistribution rights>",
  "redistribution_approved": true
}
```

The example duration is illustrative; measure the actual take. Use `Get-FileHash -Algorithm SHA256 <file>` to obtain the content hash. Keep the manifest's declared duration within 100 ms of the decoded duration. The build tests verify metadata, exact script hashes, file checksums, path/size/header bounds and one-second padding. The runtime also checks those values before publishing optional audio; a rejected registry falls back to text.

Run `python validation/validate_worldboss_voice.py` with ffprobe installed to verify actual Ogg/Vorbis decoding, channel/sample format and duration, then run `gradlew.bat --offline test build --console=plain`. With no files, the validator explicitly reports that recorded-performance release readiness is open. It does not manufacture a passing voice signoff.

Validated recordings are included in the Polymer pack under the dedicated `cobbleraids_worldboss` namespace, avoiding other modules' sound manifests. Clients must accept the resulting pack for custom voice. Readable subtitles and vanilla cues remain available when a recording is absent. There is no verified client pack-status probe, so do not claim the server knows that a given client heard a recording.

## Required live rehearsal

Test ordinary and late entrance timing, free/battling finale audiences, all preferences, skip during a spoken line, late join, reconnect, pack accept/decline/failure, Music/Voice slider behavior, exact event-owned music stop/restart, configuration replacement, new-event/season cancellation, and local replay inside/outside the viewing area. Verify that no scene changes camera, battle timing, admission, Merit, currency, blessing, awakening or mastery. Replays are presentation only.

For an isolated local preview without a live victory, staff can use `/cr worldboss scene preview <entrance|victory|escape> [awakening]` after preparing and stopping an arena test. It requires permission level 2, a saved arena, no active event, presence in the viewing sphere and no battle. It plays only to the requesting player, uses a rehearsal label and changes no campaign, transcript journal or reward. The same fifteen-second replay cooldown applies.

Audio playback is best effort. Durable scene intent prevents automatic replay after restart, but a crash after commit and before packet delivery can mean a player misses a scene; transcript and manual replay cover that case. Do not promise exactly-once client audio delivery. Keep deployment and recorded-voice quality signoff separate from unit/build success.
