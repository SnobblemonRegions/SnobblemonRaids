#!/usr/bin/env python3
"""Decode/hash gate for packaged recorded performances; an empty manifest is an explicit open gate."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/worldboss_voice"


def main():
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["version"] == 1, "Unknown voice manifest version"
    recordings = manifest["recordings"]
    if not recordings:
        print("World Boss voice assets: NO RECORDINGS -- recorded-performance release gate remains OPEN")
        return
    decoder = shutil.which("ffprobe")
    assert decoder, "ffprobe is required to validate recorded Ogg/Vorbis audio before release"
    seen = set()
    for row in recordings:
        assert re.fullmatch(r"[a-z0-9_]{1,64}", row["script"]), "Invalid script ID"
        assert row["script"] not in seen, "Duplicate recording"
        seen.add(row["script"])
        assert re.fullmatch(r"worldboss/v1/[a-z0-9_]+\.ogg", row["file"]), "Invalid audio path"
        assert row["redistribution_approved"] is True and row["performer"].strip() and row["license"].strip(), "Missing rights metadata"
        assert re.fullmatch(r"[0-9a-f]{64}", row["script_sha256"]), "Invalid script hash; Gradle tests verify exact authored-script match"
        path = ROOT / row["file"]
        raw = path.read_bytes()
        assert 32 <= len(raw) <= 4 * 1024 * 1024, "Invalid recording size"
        assert hashlib.sha256(raw).hexdigest() == row["sha256"], "Recording checksum mismatch"
        result = subprocess.run([decoder, "-v", "error", "-show_entries", "stream=codec_name,codec_type,sample_rate,channels", "-show_entries", "format=duration", "-of", "json", str(path)], capture_output=True, text=True, timeout=15, check=True)
        metadata = json.loads(result.stdout)
        assert not result.stderr.strip(), "Recording decode reported errors"
        assert len(metadata["streams"]) == 1, "Recording must contain one audio stream"
        stream = metadata["streams"][0]
        assert stream["codec_type"] == "audio" and stream["codec_name"] == "vorbis", "Minecraft voice recordings must be Ogg/Vorbis"
        assert 22050 <= int(stream["sample_rate"]) <= 48000 and stream["channels"] in (1, 2), "Unsupported recording format"
        duration_ms = float(metadata["format"]["duration"]) * 1000
        assert duration_ms > 0 and abs(duration_ms - row["duration_ms"]) <= 100, "Declared/decoded voice duration mismatch"
    print(f"World Boss recorded audio decode/hash validation: PASS -- {len(recordings)} recording(s); performance/rights/client signoff remains human-reviewed")


if __name__ == "__main__":
    main()
