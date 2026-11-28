"""Ordinary native PvP compatibility; process-owned disposable rig and synthetic accounts only."""
import argparse
import json
from pathlib import Path
import re
import secrets
import subprocess
import time
from rcon import Rcon
from worldboss_rehearsal import load, verify_owner, write_json


def run(path):
    rig, manifest, props = load(path)
    verify_owner(manifest)
    output = rig / ("ordinary-compat-" + str(time.time_ns()))
    output.mkdir()
    handles, bots, commands = [], [], []
    report = {"scope": "synthetic native PvP; not an economic or full-party signoff", "checks": []}

    def command(value):
        verify_owner(manifest)
        with Rcon("127.0.0.1", manifest["rcon_port"], props["rcon.password"]) as client:
            response = client.command(value)
        commands.append({"command": value, "response": response})
        write_json(output / "commands.json", commands)
        return response

    def check(name, passed):
        report["checks"].append({"name": name, "passed": bool(passed)})
        write_json(output / "report.json", report)
        if not passed:
            raise RuntimeError("Compatibility check failed: " + name)

    def log(name):
        return (output / (name + ".out.log")).read_text(encoding="utf-8", errors="replace")

    def wait(predicate, seconds=30):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            if predicate():
                return True
            time.sleep(.2)
        return False

    try:
        check("empty private server", "0 of a max" in command("list"))
        before = command("cr worldboss status")
        check("test-only boss fixture", "TEST encounter" in before)
        command("cr worldboss control hold")
        check("staff hold acknowledged before compatibility tests", wait(lambda: "World Boss control save: none" in command("cr worldboss status")))
        before = command("cr worldboss status")
        pool = re.search(r"Pool (\d+) / (\d+)", before)
        check("held boss has frozen HP", pool is not None and "Timing: HELD" in before)
        names = ["WBCompat_" + secrets.token_hex(2) + suffix for suffix in ("A", "B")]
        for name in names:
            stdout = (output / (name + ".out.log")).open("wb")
            stderr = (output / (name + ".err.log")).open("wb")
            handles.extend([stdout, stderr])
            bots.append(subprocess.Popen(["node", str(Path(__file__).with_name("worldboss_duelbot.js")), name,
                "127.0.0.1", str(manifest["game_port"])], stdin=subprocess.PIPE, stdout=stdout, stderr=stderr,
                text=True, creationflags=subprocess.CREATE_NO_WINDOW))
        check("compatibility clients connected", wait(lambda: all("READY" in log(name) for name in names), 90))
        for bot, name in zip(bots, names):
            command(f"tp {name} 200 65 0")
            check("ordinary party created " + name, "Gave Glaceon" in command(f"givepokemonother {name} glaceon level=100"))
            bot.stdin.write("FIGHT\n"); bot.stdin.flush()
        response = command("wbperf ordinary-duel " + " ".join(names))
        check("ordinary duel bypasses world-boss routing", "worldBossOwned=false" in response)
        check("ordinary clients receive native battle UI", wait(lambda: all("BATTLE_INIT" in log(name) for name in names)))
        check("both ordinary players submit native moves", wait(lambda: all("SENT " in log(name) for name in names)))
        if not all("BATTLE_END" in log(name) for name in names):
            for bot in bots:
                bot.stdin.write("STOP\n"); bot.stdin.flush()
            command("stopbattle " + names[0])
        check("native ordinary battle cleanup reaches both clients", wait(lambda: all("BATTLE_END" in log(name) for name in names)))
        after = command("cr worldboss status")
        after_pool = re.search(r"Pool (\d+) / (\d+)", after)
        check("ordinary duel does not alter world-boss HP", after_pool is not None and after_pool.groups() == pool.groups())
        check("private world-boss simulator stays ready", "ready (private world-boss simulator)" in command("cr worldboss preflight"))
        area_check=command("wbperf spawn-area-check " + names[0])
        report["spawn_area_routing"]=area_check
        check("native protected area routing preserves outside and mixed pools", "PASS protected/outside/mixed/reloaded/non-player/custom-generator" in area_check)
        save_check=command("wbperf player-save-check " + " ".join(names))
        report["player_save_ordering"]=save_check
        check("native player autosaves preserve forced-save state and previous backup", "PASS native automatic queue / forced drain / latest state / previous backup" in save_check)
        check("native advancement pacing retains reload and forced-save barriers", "advancement pace/read/force" in save_check)
        world_check=command("wbperf world-save-check")
        report["world_save_ordering"]=world_check
        check("native world saves retain read barriers, later mutations and metadata backup", "PASS Main serialization / native read barriers / later dirty state / forced latest / level.dat_old" in world_check)
        outside=command("wbperf card-stat-check " + names[0])
        check("ordinary Cards diagnostics retain native INFO routing", "bonus=0.0 debugCalls=0" in outside)
        bots[0].stdin.write("SAY /cr worldboss observe\n");bots[0].stdin.flush()
        def arena_card_stats():
            result=command("wbperf card-stat-check " + names[0])
            report["card_stat_arena_routing"]=result
            match=re.search(r"bonus=0\.0 debugCalls=(\d+)",result)
            return match is not None and int(match.group(1))>0
        check("arena Cards diagnostics use DEBUG with identical native bonus", wait(arena_card_stats,30))
        report["result"] = "PASS"
    finally:
        for bot in bots:
            if bot.poll() is None:
                try:
                    bot.stdin.write("QUIT\n"); bot.stdin.flush(); bot.wait(timeout=10)
                except (OSError, subprocess.TimeoutExpired):
                    bot.kill(); bot.wait(timeout=5)
            if bot.stdin:
                bot.stdin.close()
        for handle in handles:
            handle.close()
        write_json(output / "report.json", report)
        print("Evidence:", output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rig", type=Path, required=True)
    run(parser.parse_args().rig)
