"""Bounded attendance pilot, exclusively against a process-owned disposable rig.

Builds a flat arena fixture in that fresh world. Does not activate a season,
grant live rewards, establish production balance, or measure graphical-client FPS.
"""
import argparse
import json
import re
from pathlib import Path
import subprocess
import time
import secrets

from rcon import Rcon
from worldboss_rehearsal import digest, load, verify_owner, write_json


def writes_complete(response,label):
    saved=re.search(re.escape(label)+r": pending=(\d+), bytes=(\d+), submitted=(\d+), completed=(\d+), failed=(true|false)",response)
    return saved is not None and saved.group(1)=="0" and saved.group(2)=="0" \
        and saved.group(3)==saved.group(4) and saved.group(5)=="false"


def player_saves_complete(response):
    return writes_complete(response,"World Boss player autosaves")


def advancements_complete(response):
    saved=re.search(r"World Boss advancement autosaves: pending=(\d+), scheduled=(\d+), processed=(\d+), superseded=(\d+), failed=(true|false)",response)
    return saved is not None and saved.group(1)=="0" and int(saved.group(2))>0 \
        and int(saved.group(2))==int(saved.group(3))+int(saved.group(4)) and saved.group(5)=="false" \
        and writes_complete(response,"advancement IO")


def active_combat(response):
    return "Timing: ACTIVE, reason: NONE," in response


def protocols_ready(response,players):
    matched=re.search(r'protocol setup: queued=(\d+), prepared=(\d+), consumed=(\d+), failed=(\d+), fallbacks=(\d+)',response)
    return matched is not None and tuple(map(int,matched.groups()))==(players,players,players,0,0)


def run(rig, require_integration_v2=False, fighters=5, combat_seconds=0, probe=False, require_startup_warmup=False, stationary_clients=False, no_wild_spawns=False, require_natural_spawn_guard=False, exercise_player_autosave=False, require_party_profiles=False, login_only=False, diagnostic_jfr=False, require_login_pacer=False, require_poi_read_ahead=False):
    if require_poi_read_ahead and (not probe or no_wild_spawns):
        raise ValueError('POI read-ahead checks require tick recording and enabled natural Pokemon spawning')
    if login_only and (not probe or require_party_profiles or exercise_player_autosave or combat_seconds):
        raise ValueError('Cold-login-only checks require tick recording and cannot assert party/combat coverage')
    if require_party_profiles and not probe:
        raise ValueError("Party readiness requires the native tick-boundary probe")
    rig, manifest, props = load(rig)
    verify_owner(manifest)
    output = rig / ("pilot-" + str(time.time_ns()))
    output.mkdir()
    report = {"scope": f"test-only {fighters}-fighter/one-observer native pilot", "checks": [],
              "requested_fighters": fighters, "observers": 1,
              "party_fixture": "one level-100 Glaceon per fresh account; not six-Pokemon party coverage",
              "client_physics_enabled": not stationary_clients,
              "pokemon_spawning_disabled": no_wild_spawns,
              "require_natural_spawn_guard": require_natural_spawn_guard,
              "exercise_player_autosave": exercise_player_autosave,
              "require_party_profiles": require_party_profiles,
              "release_ready": False, "runtime_log_review": "required separately",
              "production_calibration": False, "client_fps": "not measured",
              "jar_sha256": manifest["jar_sha256"], "started_unix": time.time()}
    bots, handles = [], []
    commands = []
    cache = rig / "rehearsal-world" / "data" / "cobbleraids_world_boss.dat"
    journal = cache.with_suffix(".journal")
    before_cache = (cache.stat().st_mtime_ns, digest(cache))
    before_journal = digest(journal)
    probe_started = False
    jfr_started = False

    def jfr(action):
        verify_owner(manifest)
        result=subprocess.run([str(Path(manifest['java']).with_name('jcmd.exe')),str(manifest['pid']),
            'JFR.'+action,'name=wbLogin',*(['settings=profile','duration=120s','filename='+str(output/'login.jfr')] if action=='start' else [])],
            capture_output=True,text=True,timeout=30,creationflags=subprocess.CREATE_NO_WINDOW)
        report['jfr_'+action]={'code':result.returncode,'output':result.stdout,'error':result.stderr}
        if result.returncode!=0:raise RuntimeError('Diagnostic JFR '+action+' failed')

    def command(text):
        verify_owner(manifest)
        with Rcon("127.0.0.1", manifest["rcon_port"], props["rcon.password"]) as client:
            response = client.command(text)
        commands.append({"unix": time.time(), "command": text, "response": response})
        write_json(output / "commands.json", commands)
        print(text + " => " + response, flush=True)
        if re.search(r"That position is not loaded|Unknown or incomplete command|Incorrect argument|No player was found|Could not", response):
            raise RuntimeError("Native command rejected: " + text)
        return response

    def check(name, passed, evidence):
        report["checks"].append({"name": name, "passed": bool(passed), "evidence": evidence})
        write_json(output / "report.json", report)
        if not passed:
            raise RuntimeError("Pilot check failed: " + name)

    def wait_for(predicate, seconds):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if predicate():
                return True
            time.sleep(1)
        return False

    def log(name):
        return (output / (name + ".out.log")).read_text(encoding="utf-8", errors="replace")

    try:
        if require_startup_warmup:
            native_log = (rig / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace")
            check("startup simulator warmup completed", "Simulator startup turn warmup completed" in native_log,
                  "Actual native context must report successful bounded warmup before this trial")
        if probe:
            check("full-tick recording starts", "WBPERF recording" in command(f"wbperf start fighters{fighters}"), "Rehearsal sidecar only")
            probe_started = True
        if require_integration_v2:
            native_log = (rig / "logs" / "latest.log").read_text(encoding="utf-8", errors="replace")
            check("compatible integration registers", "Listening for CobbleRaids World Boss API v2" in native_log,
                  "Registration only: test encounters intentionally do not dispatch economic listeners")
        check("empty private server", "0 of a max" in command("list"), "Must be empty before fixture setup")
        command("gamerule doMobSpawning false")
        if no_wild_spawns:
            check("native Pokemon spawning disabled", "false" in command("gamerule doPokemonSpawning false"), "Cobblemon has a separate rule from vanilla mob spawning")
        if require_natural_spawn_guard:
            check("native Pokemon spawning enabled for guard trial", "true" in command("gamerule doPokemonSpawning true"), "The protected arena must reject native spawn attempts without disabling world spawning")
        report["pokemon_spawning_gamerule"] = command("gamerule doPokemonSpawning")
        command("gamerule doWeatherCycle false")
        command("gamerule doDaylightCycle false")
        command("time set noon")
        command("forceload add -160 -160 -1 159")
        command("forceload add 0 -160 159 159")
        command("forceload add 176 -16 223 15")
        time.sleep(12)
        for x in range(-130, 131, 50):
            command(f"fill {x} 64 -130 {min(x + 49, 130)} 64 130 minecraft:stone")
        command("fill 185 64 -12 215 64 12 minecraft:stone")
        command("setworldspawn 200 65 0")
        for x, y, z, kind in ((0, 65, 0, "spawn"), (54, 65, 0, "entrance"),
                               (20, 65, 0, "ward_pylon"), (-12, 65, 0, "relay_cache"),
                               (4, 65, 0, "relay_checkpoint")):
            command(f"execute unless block {x} {y} {z} cobbleraids:world_boss_{kind} run setblock {x} {y} {z} cobbleraids:world_boss_{kind}")
        command("fill 99 68 -15 125 68 15 minecraft:stone")
        command("execute unless block 105 69 0 cobbleraids:world_boss_observer run setblock 105 69 0 cobbleraids:world_boss_observer")
        command("cr worldboss arena list")
        command("cr worldboss preflight")
        prefix = f"WB{fighters}_{secrets.token_hex(2)}_"
        names = [prefix + str(i) for i in range(1, fighters + 2)]
        report["players"] = names
        if diagnostic_jfr:
            jfr('start');jfr_started=True
        if probe:
            command('wbperf mark cold_login')
        for name in names:
            out = (output / (name + ".out.log")).open("wb")
            err = (output / (name + ".err.log")).open("wb")
            handles.extend([out, err])
            bot = subprocess.Popen(["node", str(Path(__file__).with_name("worldboss_duelbot.js")),
                name, "127.0.0.1", str(manifest["game_port"]), *(["--stationary"] if stationary_clients else [])], stdin=subprocess.PIPE,
                stdout=out, stderr=err, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
            bots.append(bot)
        check(f"{len(names)} native clients connect", wait_for(lambda: all("READY" in log(n) for n in names), 120),
              {n: log(n)[-1500:] for n in names})
        if require_login_pacer:
            login_status=command('cr worldboss preflight')
            report['login_continuations']=login_status
            paced=re.search(r'World Boss login continuations: pending=(\d+), queued=(\d+), applied=(\d+), rejected=(\d+), discarded=(\d+)',login_status)
            check('native login callbacks applied exactly once after readiness',paced is not None and tuple(map(int,paced.groups()))==(0,len(names),len(names),0,0),login_status)
            check('native outbound protocol setup completes before Main admission',protocols_ready(login_status,len(names)),login_status)
        if login_only:
            time.sleep(15)
            check('all cold-login clients remain connected',str(len(names))+' of a max' in command('list'),'Native attendance after cold join, before any party grant')
            report['scope']='Cold native login only; no parties, encounter or rewards'
            report['party_fixture']=None
            report['result']='PASS: cold-login functional checks only; timing requires raw validation'
            return
        if probe:
            command("wbperf mark initialization")
        for name in names:
            command(f"tp {name} 200 65 0")
            command(f"givepokemonother {name} glaceon level=100")
        if require_party_profiles:
            check("native fixture party profiles acknowledged before encounter", wait_for(
                lambda: all("[Ascend]" in log(name) and "ascension assigned." in log(name) for name in names),60),
                "The SDK emits each native assignment notice after its acquisition transaction/project completes; initialization remains recorded as setup")
            def tick_id():
                result=re.search(r"WBPERF tick-id=(\d+)",command("wbperf tick-id"))
                if result is None:raise RuntimeError("Native tick boundary unavailable")
                return int(result.group(1))
            boundary=tick_id()
            check("fixture initialization crosses a complete native tick boundary",wait_for(lambda:tick_id()>=boundary+2,15),
                  "The encounter begins only after confirmed party initialization and two observed tick boundaries; raw setup ticks are retained")
        if exercise_player_autosave:
            idle_saves=command("wbperf automatic-player-save")
            report["idle_automatic_player_save_batch"]=idle_saves
            check("native automatic player-save batch queued before encounter", "WBPERF automatic saves PASS" in idle_saves,
                  idle_saves)
        if probe:
            command("wbperf mark gathering")
        check("test preparation accepted", "TEST preparation queued" in command("cr worldboss test start"), "Accepted is distinct from durable publication; no live season")
        for index, bot in enumerate(bots):
            bot.stdin.write("FIGHT\nSAY /cr worldboss " + ("observe" if index == fighters else "fight") + "\n")
            bot.stdin.flush()
        time.sleep(15)
        command("list")
        status = command("cr worldboss status")
        report["initial_status"] = status
        check("test preparation acknowledged before automatic admission", "World Boss control save: none" in status,
              "Continuation must finish its durable receipt before native publication and admission")
        for name in names[:min(6, len(names))]:
            command(f"execute as {name} run cr worldboss me")
            command(f"data get entity {name} Pos")
        check(f"{fighters} native battles begin", wait_for(lambda: all("BATTLE_INIT" in log(n) for n in names[:fighters]), 150),
              {n: log(n)[-2500:] for n in names})
        check("observer receives no private battle", "BATTLE_INIT" not in log(names[fighters]), log(names[fighters])[-2000:])
        check(f"{fighters} clients send real move responses", wait_for(lambda: all("SENT " in log(n) for n in names[:fighters]), 60),
              {n: log(n)[-1500:] for n in names[:fighters]})
        if probe:
            command("wbperf mark combat")
        if exercise_player_autosave:
            batch=command("wbperf automatic-player-save")
            report["automatic_player_save_batch"]=batch
            check("native automatic player-save batch exercised during combat", f"PASS players={len(names)} submitted={len(names)}" in batch, batch)
        report["combat_status_samples"] = []
        deadline = time.monotonic() + combat_seconds
        while time.monotonic() < deadline:
            sample=command("cr worldboss status")
            report["combat_status_samples"].append(sample)
            check("combat remains active at sample "+str(len(report["combat_status_samples"])),active_combat(sample),sample)
            time.sleep(min(10, max(0, deadline - time.monotonic())))
        if require_natural_spawn_guard:
            spawn_evidence = command("cr worldboss arena list")
            match = re.search(r"natural Pokemon spawns refused this server session: (\d+)", spawn_evidence)
            early = re.search(r"natural Pokemon position evaluations skipped this server session: (\d+)", spawn_evidence)
            whole = re.search(r"natural Pokemon whole-area generations skipped this server session: (\d+)", spawn_evidence)
            report["spawn_guard_evidence"] = {"entity_refusals": int(match.group(1)) if match else None,
                                              "protected_position_evaluations_skipped": int(early.group(1)) if early else None,
                                              "protected_whole_areas_skipped": int(whole.group(1)) if whole else None}
            check("native arena spawn guard exercised", (match is not None and int(match.group(1)) > 0)
                  or (early is not None and int(early.group(1)) > 0), spawn_evidence)
            check("native protected whole-area fast path exercised", whole is not None and int(whole.group(1)) > 0, spawn_evidence)
        report["tick_query_window"] = command("tick query")
        if exercise_player_autosave:
            saves=command("cr worldboss preflight")
            report["automatic_player_save_completion"]=saves
            check("accepted native player autosaves drained without failure", player_saves_complete(saves), saves)
            check("accepted native world NBT autosaves drained without failure", writes_complete(saves,"World Boss world NBT autosaves"), saves)
            check("scheduled native advancement saves finished without hook failure", advancements_complete(saves), saves)
        if probe:
            command("wbperf mark recovery")
        command("cr worldboss control hold")
        time.sleep(5)
        def hold_published():
            response=command("cr worldboss status")
            return "World Boss control save: none" in response and "Timing: HELD, reason: STAFF" in response
        check("staff hold reaches durable acknowledgement", wait_for(hold_published,30), "Combat stops immediately; durable hold is checked separately")
        check("hold has no owned duels", "duelling now: 0" in command("cr worldboss status"),
              "Server status after hold; not inferred from client disconnects")
        check("hold ends owned battles", all("BATTLE_END" in log(n) for n in names[:fighters]),
              {n: log(n)[-2000:] for n in names[:fighters]})
        command("cr worldboss control recover")
        def recovery_published():
            response=command("cr worldboss status")
            report["recovered_status"]=response
            return "World Boss control save: none" in response and "Timing: REGROUP, reason: NONE" in response
        check("staff recovery reaches durable acknowledgement", wait_for(recovery_published, 30),
              "Queued recovery must finish its receipt before regroup publication")
        command("cr worldboss status")
        check("live commits preserve cache while advancing journal",
              before_cache == (cache.stat().st_mtime_ns, digest(cache)) and before_journal != digest(journal),
              "Native admission/combat/hold/recover committed to journal without rewriting rebuildable NBT")
        report["result"] = "PASS: limited native pilot; full release matrix remains open"
    except Exception as error:
        report["result"] = "FAIL"
        report["error"] = str(error)
        raise
    finally:
        if jfr_started:
            try:
                jfr('stop')
            except Exception as error:
                report['jfr_stop_error']=str(error)
                if not (output/'login.jfr').is_file():report['result']='FAIL: diagnostic JFR evidence missing'
        if probe_started:
            try:
                response = command("wbperf stop")
                report["full_tick_probe"] = response
                if "file=" not in response:
                    report["result"] = "FAIL: full-tick evidence was not written"
                    raise RuntimeError("Full-tick probe did not save evidence")
                if require_poi_read_ahead:
                    fixture=command('wbperf poi-read-ahead-check')
                    report['poi_read_ahead_fixture']=fixture
                    if 'WBPERF POI read ahead PASS' not in fixture:
                        report['result']='FAIL: native POI read-ahead fixture failed'
            except Exception as error:
                report["probe_error"] = str(error)
                report["result"] = "FAIL: full-tick evidence incomplete"
        for bot in bots:
            if bot.poll() is None:
                try:
                    bot.stdin.write("QUIT\n")
                    bot.stdin.flush()
                    bot.wait(timeout=5)
                except (OSError, subprocess.TimeoutExpired):
                    bot.terminate()
                    bot.wait(timeout=5)
        for handle in handles:
            handle.close()
        report["ended_unix"] = time.time()
        write_json(output / "report.json", report)
        print("Evidence: " + str(output), flush=True)
        if login_only and report.get('result','FAIL').startswith('FAIL'):
            raise RuntimeError(report['result']) # An early login-only return cannot bypass evidence failure.
    if report["result"].startswith("FAIL"):
        raise RuntimeError(report["result"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rig", type=Path, required=True)
    parser.add_argument("--require-integration-v2", action="store_true")
    parser.add_argument("--fighters", type=int, choices=(5, 10, 30, 50), default=5)
    parser.add_argument("--combat-seconds", type=int, choices=range(0, 181), default=0)
    parser.add_argument("--probe", action="store_true")
    parser.add_argument('--login-only',action='store_true',help='Stop after cold native login and 15 seconds connected, before Pokemon grants')
    parser.add_argument('--diagnostic-jfr',action='store_true',help='Capture a full-loop JFR around login; permanently excludes this process from capacity approval')
    parser.add_argument('--require-login-pacer',action='store_true',help='Require one ready native final-login callback per fresh synthetic account, without rejection or duplication')
    parser.add_argument('--require-poi-read-ahead',action='store_true',help='After timing stops but before clients disconnect, require native POI invalidation and positive natural-spawn evidence')
    parser.add_argument("--exercise-player-autosave", action="store_true", help="Exercise one native automatic player-save batch inside timed combat and verify its queue drains")
    parser.add_argument("--require-party-profiles",action="store_true",help="Confirm native Ascension assignment receipts and completed tick boundaries before the encounter starts; preserve all setup ticks")
    parser.add_argument("--require-startup-warmup", action="store_true")
    parser.add_argument("--stationary-clients", action="store_true", help="Compare the same native fixture without local bot physics; teleport acknowledgements remain enabled")
    spawning = parser.add_mutually_exclusive_group()
    spawning.add_argument("--no-wild-spawns", action="store_true", help="Disable Cobblemon's own spawning rule in this private fixture")
    spawning.add_argument("--require-natural-spawn-guard", action="store_true", help="Keep Pokemon spawning enabled and require actual scoped arena cancellations")
    args = parser.parse_args()
    run(args.rig, args.require_integration_v2, args.fighters, args.combat_seconds, args.probe, args.require_startup_warmup, args.stationary_clients, args.no_wild_spawns, args.require_natural_spawn_guard, args.exercise_player_autosave, args.require_party_profiles,args.login_only,args.diagnostic_jfr,args.require_login_pacer,args.require_poi_read_ahead)
