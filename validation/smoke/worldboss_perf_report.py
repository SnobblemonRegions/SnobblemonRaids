"""Check raw full-tick rehearsal records. Never infer whole-run gates from /tick query.

Declare the phases and minimum actual population explicitly. Setup/build samples
remain in the input even when the selected event phases are checked separately.
"""
import argparse
import json
import math
from pathlib import Path


def evaluate(data, phases, min_players, joining_phase=None):
    if min_players < 1:raise ValueError('A positive actual population is required')
    if joining_phase is not None and (joining_phase!='cold_login' or joining_phase not in phases):
        raise ValueError('Joining population is only valid for an explicitly selected cold_login phase')
    if data.get("diagnosticJfrRecording"):
        raise ValueError("JFR-profiled process is diagnostic evidence and cannot approve capacity")
    if data.get("diagnosticRuntimePrototype"):
        raise ValueError("Runtime prototype instrumentation cannot approve production capacity")
    if data.get("diagnosticJavascriptTrace"):
        raise ValueError("Diagnostic bootstrap instrumentation was armed in this process; diagnostic evidence cannot approve capacity")
    rows = data.get("ticks")
    if not isinstance(rows, list) or not rows or len(rows) > 24000:
        raise ValueError("Missing, empty or oversized raw tick recording")
    seen = set()
    for row in rows:
        if (not isinstance(row, dict) or type(row.get("id")) is not int or row["id"] in seen
                or not isinstance(row.get("phase"), str)
                or any(type(row.get(k)) is not int or row[k] < 0 for k in ("players", "nanos", "intervalNanos"))):
            raise ValueError("Malformed or duplicate tick")
        seen.add(row["id"])
    if data.get("summary", {}).get("dropped", 0) or data.get("stallSamplesDropped", 0):
        raise ValueError("Recording overflowed; cannot approve incomplete evidence")
    results = {}
    for phase in phases:
        selected = [row for row in rows if row["phase"] == phase]
        if len(selected) < 100:
            raise ValueError("At least 100 complete raw ticks required for phase " + phase)
        times = sorted(row["nanos"] / 1e6 for row in selected)
        percentile = lambda fraction: times[math.ceil(len(times) * fraction) - 1]
        result = {"ticks": len(times), "p95_ms": percentile(.95), "p99_ms": percentile(.99),
                  "max_ms": max(times), "min_players": min(row["players"] for row in selected),
                  "max_players": max(row["players"] for row in selected),
                  "max_start_interval_ms": max(row["intervalNanos"] for row in selected) / 1e6}
        population=(result['max_players']>=min_players and selected[-1]['players']>=min_players) if phase==joining_phase else result['min_players']>=min_players
        result["passed"] = (result["p95_ms"] < 50 and result["p99_ms"] < 100
                            and result["max_ms"] <= 250 and result["max_start_interval_ms"] <= 250
                            and population)
        if phase==joining_phase:result['population_policy']='rising population; requested attendance must be reached and retained at phase end; all ticks retained'
        results[phase] = result
    if not results:
        raise ValueError("Explicit phases are required")
    return {"result": "PASS" if all(r["passed"] for r in results.values()) else "FAIL",
            "scope": "Declared phases on this host/modpack/fixture only; not production/client-FPS signoff",
            "min_players_required": min_players, "phases": results,
            "all_recorded_ticks": len(rows), "all_recorded_max_ms": max(row["nanos"] for row in rows) / 1e6}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recording", type=Path)
    parser.add_argument("--phase", action="append", required=True)
    parser.add_argument("--min-players", type=int, required=True)
    parser.add_argument('--joining-phase',choices=['cold_login'],help='Only cold_login may begin below the requested attendance; all raw latency samples remain mandatory')
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.min_players < 1:
            raise ValueError("A positive actual population is required")
        result = evaluate(json.loads(args.recording.read_text(encoding="utf-8")), args.phase, args.min_players,args.joining_phase)
    except (ValueError, OSError, TypeError) as error:
        result = {"result": "INCOMPLETE", "error": str(error)}
    rendered = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    raise SystemExit(0 if result["result"] == "PASS" else 1)
