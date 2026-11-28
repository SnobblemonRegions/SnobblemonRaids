import unittest
from worldboss_native_pilot import player_saves_complete,writes_complete,advancements_complete,active_combat,protocols_ready,run

class ProtocolScopeTest(unittest.TestCase):
    def test_protocol_readiness_requires_real_setup_and_exact_once_consumption(self):
        good="protocol setup: queued=51, prepared=51, consumed=51, failed=0, fallbacks=0"
        self.assertTrue(protocols_ready(good,51));self.assertFalse(protocols_ready(good,6))
        for bad in (good.replace('consumed=51','consumed=50'),good.replace('failed=0','failed=1'),good.replace('fallbacks=0','fallbacks=51'),"queued=51, applied=51, failed=0"):
            self.assertFalse(protocols_ready(bad,51))

class CombatScopeTest(unittest.TestCase):
    def test_storage_holds_or_ended_events_cannot_count_as_active_combat(self):
        self.assertTrue(active_combat("Timing: ACTIVE, reason: NONE, recovery count: 0/2"))
        for status in ("Timing: HELD, reason: STORAGE_FAILURE,", "Timing: HELD, reason: STAFF,", "Timing: REGROUP, reason: NONE,", "No World Boss is active."):
            self.assertFalse(active_combat(status))

class ColdLoginScopeTest(unittest.TestCase):
    def test_poi_evidence_requires_recording_and_cannot_disable_natural_spawning(self):
        for options in ({'probe':False},{'probe':True,'no_wild_spawns':True}):
            with self.assertRaisesRegex(ValueError,'enabled natural Pokemon spawning'):run(None,require_poi_read_ahead=True,**options)
    def test_login_only_requires_raw_timing(self):
        with self.assertRaisesRegex(ValueError,'require tick recording'):run(None,login_only=True)
    def test_login_only_cannot_claim_party_or_combat_coverage(self):
        for option in ({'require_party_profiles':True},{'exercise_player_autosave':True},{'combat_seconds':30}):
            with self.assertRaisesRegex(ValueError,'cannot assert party/combat coverage'):run(None,probe=True,login_only=True,**option)


class PlayerSaveCompletionTest(unittest.TestCase):
    def test_advancement_completion_requires_every_scheduled_job(self):
        self.assertFalse(advancements_complete("World Boss advancement autosaves: pending=1, scheduled=51, processed=50, superseded=0, failed=false"))
        self.assertFalse(advancements_complete("World Boss advancement autosaves: pending=0, scheduled=51, processed=50, superseded=0, failed=false"))

    def test_advancement_failures_cannot_borrow_a_healthy_queue(self):
        self.assertFalse(advancements_complete("World Boss advancement autosaves: pending=0, scheduled=51, processed=51, superseded=0, failed=trueWorld Boss world NBT autosaves: pending=0, bytes=0, submitted=1, completed=1, failed=false"))

    def test_advancement_native_replacements_count_toward_finished_jobs(self):
        self.assertTrue(advancements_complete("World Boss advancement autosaves: pending=0, scheduled=51, processed=50, superseded=1, failed=false; advancement IO: pending=0, bytes=0, submitted=50, completed=50, failed=false"))
    def test_advancement_completion_requires_the_disk_queue_to_finish(self):
        prefix="World Boss advancement autosaves: pending=0, scheduled=51, processed=51, superseded=0, failed=false; advancement IO: "
        self.assertFalse(advancements_complete(prefix+"pending=1, bytes=5, submitted=51, completed=50, failed=false"))
        self.assertFalse(advancements_complete(prefix+"pending=0, bytes=0, submitted=51, completed=50, failed=true"))
        self.assertFalse(advancements_complete(prefix))
        self.assertTrue(advancements_complete(prefix+"pending=0, bytes=0, submitted=51, completed=51, failed=false"))
    def status(self, fields):
        return "World Boss simulator: readyWorld Boss player autosaves: " + fields \
            + "World Boss Cards INFO output: pending=0, accepted=2, delivered=2, failed=falseWorld Boss control save: none"

    def test_failed_player_writer_cannot_borrow_another_queues_healthy_flag(self):
        self.assertFalse(player_saves_complete(self.status("pending=0, bytes=0, submitted=6, completed=5, failed=true")))

    def test_completed_count_must_match_accepted_snapshots(self):
        self.assertFalse(player_saves_complete(self.status("pending=0, bytes=0, submitted=6, completed=5, failed=false")))

    def test_outstanding_snapshot_is_not_complete(self):
        self.assertFalse(player_saves_complete(self.status("pending=1, bytes=128, submitted=6, completed=5, failed=false")))

    def test_all_accepted_player_writes_are_healthy_and_complete(self):
        self.assertTrue(player_saves_complete(self.status("pending=0, bytes=0, submitted=6, completed=6, failed=false")))

    def test_world_queue_cannot_borrow_player_completion(self):
        status=self.status("pending=0, bytes=0, submitted=6, completed=6, failed=false") \
            + "World Boss world NBT autosaves: pending=1, bytes=512, submitted=5, completed=4, failed=false"
        self.assertFalse(writes_complete(status,"World Boss world NBT autosaves"))

    def test_world_queue_requires_its_own_healthy_complete_counts(self):
        self.assertTrue(writes_complete("World Boss world NBT autosaves: pending=0, bytes=0, submitted=5, completed=5, failed=false","World Boss world NBT autosaves"))


if __name__ == "__main__":
    unittest.main()
