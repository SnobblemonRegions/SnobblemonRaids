"""Behavioural coverage for renown-affixes.js, run against the actual Showdown fork Cobblemon ships.

Same setup as validate_showdown_raid_patch_behavior.py: Cobblemon's bundled showdown.zip is extracted fresh,
this build's raid-patch.js and renown-affixes.js are dropped in beside it (exactly where
ShowdownIntegrationInstaller puts them on a live server), and showdown_renown_affixes_test.js plays real
battles with each affix through plain Node.

What this catches: an affix that no longer does what RenownAffix.java says it does, one that throws inside the
simulator, and a Showdown update that moves an event the affixes depend on. It does not cover the Java side of
the HP-pool affixes on a raid boss (RaidPoolAffix, covered by JUnit) or a live server.
"""

import io
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_showdown_raid_patch_behavior import RAID_PATCH, ROOT, find_cobblemon_jar  # noqa: E402

AFFIXES = ROOT / "src/main/resources/assets/cobbleraids/showdown/renown-affixes.js"
TEST_SCRIPT = ROOT / "validation/showdown_renown_affixes_test.js"


def main() -> None:
    cobblemon_jar = find_cobblemon_jar()
    if cobblemon_jar is None:
        print("Showdown renown affixes validation: SKIPPED -- Cobblemon jar not resolved yet"
              " (run gradlew build first)", file=sys.stderr)
        raise SystemExit(0)
    assert AFFIXES.exists(), f"{AFFIXES} is missing -- has the affix module moved?"

    with zipfile.ZipFile(cobblemon_jar) as jar:
        with jar.open("data/cobblemon/showdown.zip") as bundled:
            bundle_bytes = bundled.read()

    with tempfile.TemporaryDirectory(prefix="cobbleraids-affixes-") as tmp:
        tmp_path = Path(tmp)
        with zipfile.ZipFile(io.BytesIO(bundle_bytes)) as bundle:
            bundle.extractall(tmp_path)
        shutil.copy(RAID_PATCH, tmp_path / "raid-patch.js")
        shutil.copy(AFFIXES, tmp_path / "renown-affixes.js")

        result = subprocess.run(["node", str(TEST_SCRIPT), str(tmp_path)], capture_output=True, text=True)

    if result.returncode != 0:
        print("Showdown renown affixes validation: FAIL", file=sys.stderr)
        print(result.stdout, file=sys.stderr)
        print(result.stderr, file=sys.stderr)
        raise SystemExit(1)

    print(result.stdout.strip())


if __name__ == "__main__":
    main()
