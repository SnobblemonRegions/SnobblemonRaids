import unittest
from worldboss_perf_report import evaluate


def recording():
    return {"ticks": [{"id": i, "phase": "combat", "players": 51, "nanos": 10_000_000,
                       "intervalNanos": 50_000_000} for i in range(200)]}


class PerfReportTest(unittest.TestCase):
    def test_cold_login_retains_first_player_latency_while_population_rises(self):
        data=recording()
        for row in data['ticks']:row['phase']='cold_login'
        data['ticks'][0]['players']=0
        self.assertEqual('PASS',evaluate(data,['cold_login'],51,'cold_login')['result'])
        for key in ('nanos','intervalNanos'):
            cold=recording()
            for row in cold['ticks']:row['phase']='cold_login'
            cold['ticks'][0]['players']=0;cold['ticks'][0][key]=900_000_000
            self.assertEqual('FAIL',evaluate(cold,['cold_login'],51,'cold_login')['result'])
    def test_joining_policy_cannot_relax_combat_or_omit_actual_attendance(self):
        with self.assertRaises(ValueError):evaluate(recording(),['combat'],51,'combat')
        with self.assertRaises(ValueError):evaluate(recording(),['combat'],51,'cold_login')
        data=recording()
        for row in data['ticks']:row['phase']='cold_login';row['players']=50
        self.assertEqual('FAIL',evaluate(data,['cold_login'],51,'cold_login')['result'])
        data['ticks'][1]['players']=51
        self.assertEqual('FAIL',evaluate(data,['cold_login'],51,'cold_login')['result'])
    def test_jfr_profiled_process_cannot_approve_capacity(self):
        data = recording(); data["diagnosticJfrRecording"] = True
        with self.assertRaisesRegex(ValueError, "JFR"):
            evaluate(data, ["combat"], 51)
    def test_runtime_prototype_cannot_approve_capacity(self):
        data = recording(); data["diagnosticRuntimePrototype"] = True
        with self.assertRaisesRegex(ValueError, "prototype"):
            evaluate(data, ["combat"], 51)
    def test_instrumented_javascript_process_cannot_approve_capacity(self):
        data = recording(); data["diagnosticJavascriptTrace"] = True
        with self.assertRaisesRegex(ValueError, "instrumentation"):
            evaluate(data, ["combat"], 51)
    def test_complete_raw_phase_with_actual_attendance_passes(self):
        result = evaluate(recording(), ["combat"], 51)
        self.assertEqual("PASS", result["result"])
        self.assertEqual(200, result["phases"]["combat"]["ticks"])

    def test_one_stall_fails_even_when_p99_still_looks_good(self):
        data = recording(); data["ticks"][-1]["nanos"] = 949_227_000
        result = evaluate(data, ["combat"], 51)
        self.assertEqual("FAIL", result["result"])
        self.assertEqual(10, result["phases"]["combat"]["p99_ms"])
        self.assertEqual(949.227, result["phases"]["combat"]["max_ms"])

    def test_outer_dispatch_stall_and_population_shortfall_fail(self):
        for key, value in (("intervalNanos", 900_000_000), ("players", 30)):
            data = recording(); data["ticks"][-1][key] = value
            self.assertEqual("FAIL", evaluate(data, ["combat"], 51)["result"])

    def test_setup_is_retained_when_declared_combat_is_selected(self):
        data = recording(); data["ticks"].append({"id": 201, "phase": "setup", "players": 0,
                                                  "nanos": 900_000_000, "intervalNanos": 1})
        result = evaluate(data, ["combat"], 51)
        self.assertEqual("PASS", result["result"])
        self.assertEqual(900, result["all_recorded_max_ms"])
        self.assertEqual(201, result["all_recorded_ticks"])

    def test_overflow_absent_short_or_malformed_evidence_never_passes(self):
        data = recording(); data["summary"] = {"dropped": 1}
        with self.assertRaises(ValueError): evaluate(data, ["combat"], 51)
        with self.assertRaises(ValueError): evaluate(recording(), ["absent"], 51)
        data = recording(); data["ticks"] = data["ticks"][:5]
        with self.assertRaises(ValueError): evaluate(data, ["combat"], 51)
        data = recording(); data["ticks"][-1]["id"] = 0
        with self.assertRaises(ValueError): evaluate(data, ["combat"], 51)


if __name__ == "__main__":
    unittest.main()
