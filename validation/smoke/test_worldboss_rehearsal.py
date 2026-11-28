"""Safety gates for the native rehearsal runner. No live server is contacted."""
import json
import argparse
from pathlib import Path
import tempfile
import os
import subprocess
import sys
import unittest
from unittest.mock import patch

import worldboss_rehearsal as rehearsal
from worldboss_process import validate_identity,WindowsProcess,previous_exited


class RehearsalSafetyTest(unittest.TestCase):
    @unittest.skipUnless(os.name=='nt','Windows process handles only')
    def test_real_owned_child_is_terminated_through_its_verified_handle(self):
        child=subprocess.Popen([sys.executable,'-c','import time;time.sleep(30)'],creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            with WindowsProcess(child.pid,terminate=True) as handle:
                identity=handle.identity();self.assertTrue(handle.alive())
                validate_identity({'java':str(Path(sys.executable).resolve()),'process_creation_filetime':identity['creation_filetime']},identity)
                handle.crash();self.assertFalse(handle.alive())
                self.assertTrue(previous_exited({'pid':child.pid,'java':identity['exe'],'process_creation_filetime':identity['creation_filetime']}))
            self.assertEqual(child.wait(timeout=5),0xC0B0)
        finally:
            if child.poll() is None:child.kill();child.wait(timeout=5)

    def test_crash_requires_process_birth_and_exact_executable(self):
        manifest={"java":"C:\\jdk\\java.exe", "process_creation_filetime":123}
        validate_identity(manifest,{"exe":manifest["java"],"creation_filetime":123})
        for change in ({"creation_filetime":124},{"exe":"C:\\other\\java.exe"}):
            with self.subTest(change=change),self.assertRaises(ValueError):
                validate_identity(manifest,{"exe":manifest["java"],"creation_filetime":123}|change)
        with self.assertRaises(ValueError):
            validate_identity({"java":manifest["java"]},{"exe":manifest["java"],"creation_filetime":123})

    def test_wrong_birth_never_terminates_process(self):
        manifest={"pid":1234,"java":"C:\\jdk\\java.exe","process_creation_filetime":123}
        with patch.object(rehearsal,"load",return_value=(Path("unused"),manifest,{})), \
                patch.object(rehearsal,"WindowsProcess") as process,patch.object(rehearsal,"verify_owner") as verify:
            handle=process.return_value.__enter__.return_value
            handle.identity.return_value={"exe":manifest["java"],"creation_filetime":124}
            with self.assertRaises(ValueError):rehearsal.crash(argparse.Namespace(rig=Path("unused")))
            verify.assert_not_called();handle.crash.assert_not_called()

    def test_rejected_listener_ownership_never_terminates_process(self):
        manifest={"pid":1234,"java":"C:\\jdk\\java.exe","process_creation_filetime":123}
        with patch.object(rehearsal,"load",return_value=(Path("unused"),manifest,{})), \
                patch.object(rehearsal,"WindowsProcess") as process, \
                patch.object(rehearsal,"verify_owner",side_effect=ValueError("wrong listener")):
            handle=process.return_value.__enter__.return_value
            handle.identity.return_value={"exe":manifest["java"],"creation_filetime":123}
            with self.assertRaises(ValueError):rehearsal.crash(argparse.Namespace(rig=Path("unused")))
            handle.crash.assert_not_called()

    def test_restart_of_live_process_never_spawns_or_touches_logs(self):
        with patch.object(rehearsal,"load",return_value=(Path("unused"),{"pid":1234},{})), \
                patch.object(rehearsal,"previous_exited",return_value=False), \
                patch.object(rehearsal.subprocess,"Popen") as process:
            with self.assertRaises(ValueError):rehearsal.launch(argparse.Namespace(rig=Path("unused"),restart=True))
            process.assert_not_called()

    def test_ports_must_be_distinct_and_nonprivileged(self):
        for ports in ([25741, 25741], [0, 25742], [25741, 65536]):
            with self.subTest(ports=ports), self.assertRaises(ValueError):
                rehearsal.ports_free(ports)

    def test_unmarked_existing_server_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(FileNotFoundError):
                rehearsal.load(Path(directory))

    def test_changed_private_target_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            rig = Path(directory) / "worldboss-rehearsal-test"
            rig.mkdir()
            rehearsal.write_json(rig / "rehearsal.json", {
                "format": 1, "throwaway": True, "game_port": 25741, "rcon_port": 25742})
            settings = ("server-ip=127.0.0.1\nlevel-name=rehearsal-world\nenable-rcon=true\n"
                        "server-port=25741\nrcon.port=25742\n")
            (rig / "server.properties").write_text(settings)
            self.assertEqual(rehearsal.load(rig)[1]["game_port"], 25741)
            for before, after in (("127.0.0.1", "0.0.0.0"), ("rehearsal-world", "world"),
                                  ("25742", "25575"), ("true", "false")):
                (rig / "server.properties").write_text(settings.replace(before, after))
                with self.subTest(after=after), self.assertRaises(ValueError):
                    rehearsal.load(rig)

    def check_owner(self, owned):
        manifest = {"pid": 1234, "java": "C:\\jdk\\java.exe",
                    "launcher": "L:\\test\\fabric-server-launch.jar",
                    "game_port": 25741, "rcon_port": 25742}
        with patch.object(rehearsal.subprocess, "run") as run:
            run.return_value.stdout = json.dumps(owned)
            rehearsal.verify_owner(manifest)

    def test_missing_or_reused_process_is_rejected(self):
        for owned in ({"exe": None, "command": None},
                      {"exe": "C:\\jdk\\java.exe", "command": "another-server.jar"}):
            with self.subTest(owned=owned), self.assertRaises(ValueError):
                self.check_owner(owned)

    def test_ports_must_be_private_and_owned(self):
        owned = {"exe": "C:\\jdk\\java.exe", "command": "java -jar L:\\test\\fabric-server-launch.jar"}
        for listeners in ([], [{"LocalAddress": "127.0.0.1", "LocalPort": 25741}],
                          [{"LocalAddress": "0.0.0.0", "LocalPort": 25741},
                           {"LocalAddress": "127.0.0.1", "LocalPort": 25742}]):
            with self.subTest(listeners=listeners), self.assertRaises(ValueError):
                self.check_owner(dict(owned, listeners=listeners))
        self.check_owner(dict(owned, listeners=[
            {"LocalAddress": "127.0.0.1", "LocalPort": p} for p in (25741, 25742)]))

    def test_rejected_ownership_never_connects_rcon(self):
        with patch.object(rehearsal, "load", return_value=(Path("unused"), {}, {})), \
                patch.object(rehearsal, "verify_owner", side_effect=ValueError("wrong process")), \
                patch.object(rehearsal, "Rcon") as rcon:
            with self.assertRaises(ValueError):
                rehearsal.command(argparse.Namespace(rig=Path("unused"), text="stop"))
            rcon.assert_not_called()

    def test_modified_jar_prevents_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            rig = Path(directory)
            (rig / "mods").mkdir()
            (rig / "mods" / "test.jar").write_bytes(b"changed")
            with patch.object(rehearsal, "load", return_value=(rig, {
                    "pid": None, "jar": "test.jar", "jar_sha256": "wrong"}, {})), \
                    patch.object(rehearsal.subprocess, "Popen") as process:
                with self.assertRaises(ValueError):
                    rehearsal.launch(argparse.Namespace(rig=rig))
                process.assert_not_called()


    def test_modified_integration_jar_prevents_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            rig = Path(directory)
            (rig / "mods").mkdir()
            jar = rig / "mods" / "test.jar"
            jar.write_bytes(b"main")
            (rig / "mods" / "AscensionLib-test.jar").write_bytes(b"changed dependency")
            manifest = {"pid": None, "jar": jar.name, "jar_sha256": rehearsal.digest(jar),
                        "mods": [{"name": "AscensionLib-test.jar", "sha256": "wrong"}]}
            with patch.object(rehearsal, "load", return_value=(rig, manifest, {})), \
                    patch.object(rehearsal.subprocess, "Popen") as process:
                with self.assertRaises(ValueError):
                    rehearsal.launch(argparse.Namespace(rig=rig))
                process.assert_not_called()


if __name__ == "__main__":
    unittest.main()
