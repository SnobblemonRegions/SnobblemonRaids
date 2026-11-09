package com.cobbleraids.reward.points;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cobbleraids.catching.RaidPlayerRecord;
import com.cobbleraids.config.CobbleRaidsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RaidPointsDailyCapTest {

    private static final long TODAY = 20_400L;

    @Test
    @DisplayName("below the cap an award is paid in full")
    void belowCapPaysInFull() {
        assertEquals(36, RaidPointsDailyCap.allowance(0, 300, 36));
        assertEquals(36, RaidPointsDailyCap.allowance(264, 300, 36));
    }

    @Test
    @DisplayName("the award that crosses the cap is paid up to it, and the next pays nothing")
    void crossingAwardIsTrimmed() {
        assertEquals(10, RaidPointsDailyCap.allowance(290, 300, 75));
        assertEquals(0, RaidPointsDailyCap.allowance(300, 300, 25));
        // A cap lowered mid-day below what was already earned must not read as a debt.
        assertEquals(0, RaidPointsDailyCap.allowance(450, 300, 25));
    }

    @Test
    @DisplayName("a cap of zero means no cap, and non-positive awards pay nothing")
    void zeroCapIsUncapped() {
        assertEquals(5_000, RaidPointsDailyCap.allowance(1_000_000, 0, 5_000));
        assertEquals(0, RaidPointsDailyCap.allowance(0, 300, 0));
        assertEquals(0, RaidPointsDailyCap.allowance(0, 300, -10));
    }

    @Test
    @DisplayName("each source counts only its own earnings, and a new day starts from zero")
    void earningsAreTrackedPerSourceAndDay() {
        RaidPlayerRecord record = RaidPlayerRecord.EMPTY
                .withPointsEarned(RaidPointsSource.RAID, TODAY, 250)
                .withPointsEarned(RaidPointsSource.RAID, TODAY, 40)
                .withPointsEarned(RaidPointsSource.TOWER, TODAY, 100);

        assertEquals(290, record.pointsEarnedOn(RaidPointsSource.RAID, TODAY));
        assertEquals(100, record.pointsEarnedOn(RaidPointsSource.TOWER, TODAY));
        assertEquals(0, record.pointsEarnedOn(RaidPointsSource.RAID, TODAY + 1));
        assertEquals(25, record.withPointsEarned(RaidPointsSource.RAID, TODAY + 1, 25)
                .pointsEarnedOn(RaidPointsSource.RAID, TODAY + 1));
    }

    @Test
    @DisplayName("earnings survive the record's other updates")
    void earningsSurviveOtherWrites() {
        RaidPlayerRecord record = RaidPlayerRecord.EMPTY.withPointsEarned(RaidPointsSource.RAID, TODAY, 120);

        assertEquals(120, record.withPoints(50).withCatch().pointsEarnedOn(RaidPointsSource.RAID, TODAY));
    }

    @Test
    @DisplayName("shipped caps: 300 a day from raids, 150 from towers")
    void shippedCaps() {
        CobbleRaidsConfig.RaidPoints shipped = CobbleRaidsConfig.RaidPoints.defaults();
        assertEquals(300, shipped.dailyCapFor(RaidPointsSource.RAID));
        assertEquals(150, shipped.dailyCapFor(RaidPointsSource.TOWER));
    }

    @Test
    @DisplayName("negative caps are refused at construction")
    void capsAreBounded() {
        assertThrows(IllegalArgumentException.class,
                () -> new CobbleRaidsConfig.RaidPoints(true, 25, 50, 75, 100, -1, 150));
    }
}
