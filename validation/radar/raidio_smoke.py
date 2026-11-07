"""Raidio on a real dedicated server, heard through a vanilla protocol client.

A raid spawns due south of the player and the bot opens the radar (Raidio is the default). The player is then turned with /tp, and the sound packets the
server sends to them are checked: silent when still and facing away; the tier's tune (a square-wave lead and a bass for a Starter) at the pitches and the
tempo the distance calls for when facing it; only quiet bleeps and the odd faint tick while sweeping away; silent again after the sweep stops; and silent once
Raidio is tuned out. This proves what the server sends and when. It does not prove how it sounds: that needs ears.

Install the jar in --server-dir first. Never point this at a production server/world: it boots and stops the rig and changes its world.
"""
from pathlib import Path
import argparse, json, math, queue, subprocess, sys, threading, time
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'smoke'))
from smoke_test import Server, read_password, rcon_port, server_port, COMMAND_ERROR
from rcon import Rcon

# Raid boss "charizard" is a Starter raid: its tune's lead notes, in semitones above the note the distance gives (RaidioMotifs.STARTER).
STARTER_LEAD = [0, 4, 7, 4, 12, 7, 4, 0]
FAR, NEAR, RANGE = 1500.0, 40.0, 12
SLOW_BPM, FAST_BPM = 50.0, 130.0
# Where the other note-block sounds sit in the real sound registry, relative to the square-wave lead (block.note_block.bit), read from the order SoundEvents
# registers them in. minecraft-data's own table numbers them differently, so the lead is found as the sound heard most and the rest are placed from it.
BASS_BELOW_BIT, HAT_BELOW_BIT = 13, 7
RAID_Z = 120
# Raidio plays on the "Jukebox/Note Blocks" volume slider, so a player can turn it down; the protocol calls that category "record".
CATEGORY = 'record'
# The menu's "Tune in / Tune out" button (RadarMenuLayout.TOGGLE). The bot's own "use" clicks slot 22, "Next raid", which also opens a closed radar.
TUNE_SLOT = 20


def raidio(entries):
    """Only what Raidio itself plays: the world makes other sounds around the player (a creature shuffling, say), and those are not under test."""
    return [e for e in entries if str(e['category']) == CATEGORY]


def base_note(distance):
    if distance >= FAR: return 0
    if distance <= NEAR: return RANGE
    return round(RANGE * (math.log(FAR) - math.log(distance)) / (math.log(FAR) - math.log(NEAR)))


def pitch(note):
    return 2 ** ((min(max(note, 0), 24) - 12) / 12)


def bpm(distance):
    closeness = 0 if distance >= FAR else 1 if distance <= NEAR else (math.log(FAR) - math.log(distance)) / (math.log(FAR) - math.log(NEAR))
    return SLOW_BPM + (FAST_BPM - SLOW_BPM) * closeness


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--server-dir', type=Path, required=True)
    parser.add_argument('--java', type=Path, default=Path(r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.1+1\bin\java.exe'))
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    server = Server(args.server_dir.resolve(), args.java)
    events, messages, checks = queue.Queue(), [], []
    bot = None

    def pump():
        for line in bot.stdout:
            messages.append(line)
            if line.startswith('RADAR:'):
                events.put(json.loads(line[6:]))

    def wait(predicate, timeout=45):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try: event = events.get(timeout=.25)
            except queue.Empty:
                if bot.poll() is not None: raise AssertionError('Radar bot exited: ' + ''.join(messages[-8:]))
                continue
            if predicate(event): return event
        raise AssertionError('Timed out waiting for bot: ' + ''.join(messages[-8:]))

    request_id = 0

    def action(name, **kwargs):
        nonlocal request_id
        request_id += 1
        bot.stdin.write(json.dumps(dict(action=name, id=request_id, **kwargs)) + '\n'); bot.stdin.flush()
        response = wait(lambda e: e.get('event') == 'result' and e.get('id') == request_id)
        assert response['ok'], response
        return response

    def sound_id(entry):
        sound = entry['sound']
        return sound['soundId'] if isinstance(sound, dict) and 'soundId' in sound else sound

    try:
        server.start(); server.wait_until_ready()
        bot = subprocess.Popen(['node', str(HERE / 'radar_bot.js'), str(server_port(args.server_dir))],
                               stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
        threading.Thread(target=pump, daemon=True).start()
        wait(lambda e: e.get('event') == 'ready', 90)
        with Rcon('127.0.0.1', rcon_port(args.server_dir), read_password(args.server_dir)) as rcon:
            def command(text):
                response = rcon.command(text)
                print(text.split(' ')[0] + ': ' + response[:160], flush=True)
                assert not COMMAND_ERROR.search(response), response
                return response

            def face(yaw):
                rcon.command(f'tp RadarProbe 0.5 4 0.5 {yaw} 0')

            def is_open():
                """Whether the radar in the player's hand is tuned in, read from the item itself on the server."""
                return 'cobbleraids_radar_open: 1b' in rcon.command('data get entity RadarProbe Inventory')

            def listen(seconds):
                time.sleep(seconds)
                return action('sounds')['sounds']

            rcon.command('kill @e[type=cobblemon:pokemon]')   # raids from an earlier run stay in the world, and a nearer one would change what the player is tuned to
            command('gamemode survival RadarProbe')
            command('fill -16 3 -16 16 3 16 minecraft:stone')
            command('forceload add -16 100 16 140'); time.sleep(3)
            command(f'fill -8 3 {RAID_Z - 10} 8 3 {RAID_Z + 10} minecraft:stone')
            command('clear RadarProbe'); command('time set day'); command('weather clear')
            command(f'cobbleraids spawn charizard 0 4 {RAID_Z}')
            command('item replace entity RadarProbe hotbar.0 with cobbleraids:raid_radar')
            face(180); time.sleep(1)
            action('equip'); time.sleep(1)
            action('use'); time.sleep(2.5)   # opens the menu and clicks "Tune in"; the first refresh tunes it
            assert is_open(), 'The menu click should have tuned Raidio in'
            action('sounds')
            distance = RAID_Z - 0.5

            # A. Tuned in, standing still, facing away: silent. (The turn from the start is a sweep, so let it pass first.)
            listen(1.5)
            quiet = raidio(listen(2.5))
            assert not quiet, f'Raidio made sound standing still facing away: {quiet[:3]}'
            checks.append('Silent while standing still facing away from the raid')

            # B. Facing the raid: the Starter tune, a lead and a bass, at the pitches and tempo the distance calls for, and no static.
            face(0); listen(1.0)
            lead_pitches = {round(pitch(base_note(distance) + m), 3) for m in STARTER_LEAD}
            bass_pitch = round(pitch(base_note(distance)), 3)
            seconds = 5.0
            everything = listen(seconds)
            tone = raidio(everything)
            assert tone, f'Expected a tune while facing the raid, got nothing out of {len(everything)} sounds'
            counts = {}
            for e in tone: counts[sound_id(e)] = counts.get(sound_id(e), 0) + 1
            bit = max(counts, key=counts.get)   # the lead is the voice heard most
            assert set(counts) <= {bit, bit - BASS_BELOW_BIT}, f'A Starter tune is a lead and a bass only; heard sound ids {sorted(counts)} around lead {bit}'
            lead = [e for e in tone if sound_id(e) == bit]
            bass = [e for e in tone if sound_id(e) == bit - BASS_BELOW_BIT]
            heard = {round(e['pitch'], 3) for e in lead}
            assert heard <= lead_pitches, f'Lead pitches {sorted(heard)} are not in the tune for {distance:.0f} blocks: {sorted(lead_pitches)}'
            assert len(heard) >= 3, f'The tune should walk through its notes, only heard {sorted(heard)}'
            assert bass and {round(e['pitch'], 3) for e in bass} == {bass_pitch}, f'The bass should sit on the note the distance gives ({bass_pitch}), heard {[e["pitch"] for e in bass]}'
            assert all(0 < e['volume'] <= 1 for e in tone), 'volume out of range'
            # A sixteenth note lasts 15 / bpm seconds and the Starter tune plays 8 of its 16 positions, so it plays bpm / 30 notes a second.
            expected = bpm(distance) / 30 * seconds
            assert 0.6 * expected <= len(lead) <= 1.4 * expected, f'{len(lead)} lead notes in {seconds:.0f}s, expected about {expected:.0f} at {bpm(distance):.0f} BPM'
            checks.append(f'Facing the raid {distance:.0f} blocks away plays the Starter tune: {len(lead)} lead notes in {seconds:.0f}s (about {expected:.0f} expected at {bpm(distance):.0f} BPM), '
                          f'lead pitches {sorted(heard)}, bass on {bass_pitch}')

            # C. Sweeping away from the raid: only quiet tuning bleeps and the odd faint tick, never the tune.
            action('sounds')
            for i in range(24):
                face(180 + i * 4); time.sleep(0.1)
            sweep = raidio(action('sounds')['sounds'])
            kinds = {sound_id(e) for e in sweep}
            assert kinds, 'A sweep away from the raid made no sound at all'
            assert kinds <= {bit, bit - HAT_BELOW_BIT}, f'Sweeping away should be bleeps (lead {bit}) and ticks ({bit - HAT_BELOW_BIT}) only, heard sound ids {sorted(kinds)}'
            bleeps = [e for e in sweep if sound_id(e) == bit]
            ticks = [e for e in sweep if sound_id(e) == bit - HAT_BELOW_BIT]
            assert len(bleeps) >= 2, f'A sweep should have tuning bleeps, heard {len(bleeps)}'
            assert all(e['volume'] <= 0.25 for e in bleeps), f'Bleeps must be quiet, much quieter than the tune: {[round(e["volume"], 2) for e in bleeps]}'
            assert len(ticks) <= 10, f'The noise tick must stay sparse, heard {len(ticks)} in a short sweep'
            checks.append(f'Sweeping away plays only quiet bleeps ({len(bleeps)}) and faint ticks ({len(ticks)}), no snare and no tune')

            # D. Sweep over, still facing away: silent again.
            listen(1.5)
            quiet = raidio(listen(2.5))
            assert not quiet, f'Raidio kept sounding after the sweep stopped: {quiet[:3]}'
            checks.append('Silent again once the sweep stops')

            # E. Tuned out: facing the raid makes no sound.
            action('use', menu=True); time.sleep(1.5)   # opens the menu...
            menu = action('menu')
            assert menu['title'], 'The radar menu did not open'
            assert 'Raidio' in json.dumps(menu['title']), f'The menu should be Raidio\'s: {menu["title"]}'
            action('menu_click', slot=TUNE_SLOT); time.sleep(1.5)   # ...and clicks "Tune out"
            assert not is_open(), 'The menu click should have tuned Raidio out, but the radar is still open on the server'
            face(0); time.sleep(1.5); action('sounds')
            quiet = raidio(listen(2.5))
            assert not quiet, f'A tuned-out Raidio made sound: {quiet[:3]}'
            checks.append('Silent once tuned out, even facing the raid')

            # F. Choosing a raid: a second, nearer raid appears to the north. Raidio tunes to the nearest, so facing the far raid is silent until it is chosen
            # with sneak + use; and the choice must survive walking out of the radar's 96-block view window (it used to be forgotten there).
            command('forceload add -16 -80 16 -40'); command('forceload add 134 -16 166 16'); time.sleep(3)
            command('fill -8 3 -70 8 3 -50 minecraft:stone'); command('fill 142 3 -8 158 3 8 minecraft:stone')
            command('cobbleraids spawn charizard 0 4 -60')
            face(0); time.sleep(1)
            action('use'); time.sleep(3)   # tune in again: the nearest raid is the new one, to the north
            assert is_open(), 'The radar should be tuned in again'
            action('sounds'); listen(1.5)
            quiet = raidio(listen(2.5))
            assert not quiet, f'Facing the far raid while tuned to the nearer one should be silent: {quiet[:3]}'
            checks.append('Tunes to the nearest raid first: facing a farther one is silent')

            action('use', sneak=True)   # sneak + use: next raid
            time.sleep(1.0); action('sounds')
            chosen = raidio(listen(2.5))
            assert len([e for e in chosen if round(e['pitch'], 3) in lead_pitches]) >= 3, f'Cycling to the far raid should tune to it at once, heard {len(chosen)} sounds'
            checks.append('Sneak + use cycles to the next raid and tunes to it at once')

            command('tp RadarProbe 150.5 4 0.5 51 0')   # 150 blocks east: well outside the 96-block window round the view's centre (x 32), and the farther raid is still south-west of here
            time.sleep(1.5); action('sounds')
            far = raidio(listen(3.0))
            far_distance = math.hypot(150.5 - 1.0, 121.0 - 0.5)
            far_pitches = {round(pitch(base_note(far_distance) + m), 3) for m in STARTER_LEAD}
            assert len([e for e in far if round(e['pitch'], 3) in far_pitches]) >= 3, \
                f'The chosen raid should still be the one heard after walking out of the view window, heard {len(far)} Raidio sounds'
            checks.append(f'The chosen raid survives a 150-block walk out of the view window: still tuned to it at {far_distance:.0f} blocks')
        (args.output_dir / 'raidio-result.json').write_text(json.dumps({'passed': True, 'checks': checks}, indent=1), encoding='utf-8')
        print('RAIDIO SMOKE: PASS'); [print(' -', c) for c in checks]
    finally:
        try:
            if bot: bot.kill()
        except Exception: pass
        server.stop()


if __name__ == '__main__':
    main()
