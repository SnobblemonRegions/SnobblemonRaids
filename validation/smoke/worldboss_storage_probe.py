"""Measure forced-write latency in explicitly chosen directories, without a server.

Uses only private, automatically removed temporary files. These OS fsync timings
are a storage diagnostic, not Minecraft tick percentiles or production signoff.
"""
import argparse
import json
import math
from pathlib import Path
import os
import statistics
import tempfile
import time


def probe(directory, samples):
    directory = directory.resolve(strict=True)
    if not directory.is_dir() or not 16 <= samples <= 4096:
        raise ValueError("Existing directory and 16..4096 samples required")
    durations = []
    with tempfile.TemporaryFile(dir=directory) as file:
        for _ in range(samples):
            start = time.perf_counter_ns()
            file.write(b"x" * 4096)
            file.flush()
            os.fsync(file.fileno())
            durations.append((time.perf_counter_ns() - start) / 1_000_000)
    ordered = sorted(durations)
    return {"directory": str(directory), "samples": samples, "bytes_per_append": 4096,
            "median_ms": statistics.median(ordered),
            "p95_ms": ordered[math.ceil(samples * .95) - 1], "max_ms": max(ordered),
            "durations_ms": durations}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", action="append", type=Path, required=True)
    parser.add_argument("--samples", type=int, default=32)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = {"scope": "OS fsync diagnostic; no tick or capacity claim", "runs": [
        probe(directory, args.samples) for directory in args.directory]}
    rendered = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
