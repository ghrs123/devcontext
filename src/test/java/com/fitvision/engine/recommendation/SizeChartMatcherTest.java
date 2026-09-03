package com.fitvision.engine.recommendation;

import com.fitvision.domain.recommendation.Gender;
import com.fitvision.domain.sizechart.SizeEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SizeChartMatcher.
 * No Spring context — plain JUnit 5.
 *
 * Tests are in the same package as SizeChartMatcher so that the package-private
 * BodyProfile constructor is accessible for setting up fixture profiles with
 * precise measurement values.
 */
class SizeChartMatcherTest {

    private static final double DELTA = 0.01;

    private SizeChartMatcher matcher;

    /**
     * A "normal" body profile: bmi=22.0 (within range), estimatedChest=90, estimatedWaist=75, estimatedHip=95.
     * heightCm=165 for height-dimension tests.
     */
    private BodyProfile normalProfile;

    /**
     * An "out of range" body profile: bmi=50 (>45), estimatedChest=100, estimatedWaist=90, estimatedHip=105.
     */
    private BodyProfile outOfRangeProfile;

    @BeforeEach
    void setUp() {
        matcher = new SizeChartMatcher();

        // bmi=22.0 within [15,45] → NORMAL
        normalProfile = new BodyProfile(165, 60, Gender.FEMALE, 30,
                22.0, 28.0, 90.0, 75.0, 95.0);

        // bmi=50.0 outside [15,45] → OUT_OF_RANGE
        outOfRangeProfile = new BodyProfile(150, 110, Gender.MALE, 30,
                50.0, 30.0, 100.0, 90.0, 105.0);
    }

    // -------------------------------------------------------------------------
    // Empty / null entry list
    // -------------------------------------------------------------------------

    @Test
    void given_emptyEntryList_when_match_then_returnsNoMatch() {
        MatchResult result = matcher.match(normalProfile, List.of());

        assertEquals(MatchResult.MatchQuality.NO_MATCH, result.getQuality());
        assertNull(result.getRecommendedSize());
        assertEquals(0.0, result.getConfidenceScore(), DELTA);
    }

    @Test
    void given_nullEntryList_when_match_then_returnsNoMatch() {
        MatchResult result = matcher.match(normalProfile, null);

        assertEquals(MatchResult.MatchQuality.NO_MATCH, result.getQuality());
        assertNull(result.getRecommendedSize());
    }

    // -------------------------------------------------------------------------
    // Single entry — all dimensions match → EXACT
    // -------------------------------------------------------------------------

    @Test
    void given_singleEntry_when_allDimensionsMatch_then_returnsExact() {
        // normalProfile: chest=90, waist=75, hip=95
        SizeEntry entry = entryWithChestWaistHip("M", 85, 95, 70, 80, 90, 100);

        MatchResult result = matcher.match(normalProfile, List.of(entry));

        assertEquals(MatchResult.MatchQuality.EXACT, result.getQuality());
        assertEquals(1.0, result.getConfidenceScore(), DELTA);
        assertEquals("M", result.getRecommendedSize());
    }

    // -------------------------------------------------------------------------
    // Single entry — 2 of 3 dimensions match → PARTIAL, score ≈ 0.67
    // -------------------------------------------------------------------------

    @Test
    void given_singleEntry_when_chestAndWaistContainedButHipFarOff_then_returnsPartial() {
        // normalProfile: chest=90 (contained), waist=75 (contained), hip=95 (range 50–70, far off → 0)
        SizeEntry entry = entryWithChestWaistHip("S", 85, 95, 70, 80, 50, 70);

        MatchResult result = matcher.match(normalProfile, List.of(entry));

        assertEquals(MatchResult.MatchQuality.PARTIAL, result.getQuality());
        // weighted: chest 0.34 + waist 0.34 + hip 0 = 0.68 over 0.88 available ≈ 0.77
        assertEquals(0.77, result.getConfidenceScore(), 0.02);
        assertEquals("S", result.getRecommendedSize());
    }

    // -------------------------------------------------------------------------
    // Single entry — dimensions just outside the range → CLOSEST
    // -------------------------------------------------------------------------

    @Test
    void given_singleEntry_when_everyDimensionSlightlyOutsideRange_then_returnsClosest() {
        // normalProfile: chest=90, waist=75, hip=95 — each ~3cm outside the entry's bounds
        SizeEntry entry = entryWithChestWaistHip("XS", 84, 87, 78, 82, 89, 92);

        MatchResult result = matcher.match(normalProfile, List.of(entry));

        assertEquals(MatchResult.MatchQuality.CLOSEST, result.getQuality());
        assertEquals("XS", result.getRecommendedSize());
        assertTrue(result.getConfidenceScore() < 0.6 && result.getConfidenceScore() > 0.3,
                "closest confidence should be modest, got " + result.getConfidenceScore());
    }

    // -------------------------------------------------------------------------
    // Single entry — nothing credibly fits → NO_MATCH
    // -------------------------------------------------------------------------

    @Test
    void given_singleEntry_when_everyDimensionFarOff_then_returnsNoMatch() {
        // normalProfile: chest=90, waist=75, hip=95 — all far below this tiny range
        SizeEntry entry = entryWithChestWaistHip("XS", 50, 60, 40, 50, 55, 65);

        MatchResult result = matcher.match(normalProfile, List.of(entry));

        assertEquals(MatchResult.MatchQuality.NO_MATCH, result.getQuality());
        assertNull(result.getRecommendedSize());
    }

    // -------------------------------------------------------------------------
    // Multiple entries — pick the best match
    // -------------------------------------------------------------------------

    @Test
    void given_multipleEntries_when_scoresTie_then_prefersEntryWithMoreContainedDimensions() {
        // Entry S: only chest bounded (contained) → overallScore 1.0 but a single dimension
        // Entry M: chest, waist, hip all contained → overallScore 1.0 AND EXACT-eligible
        SizeEntry entryS = entryWithChestOnly("S", 85, 95);
        SizeEntry entryM = entryWithChestWaistHip("M", 85, 95, 70, 80, 90, 100);

        MatchResult result = matcher.match(normalProfile, List.of(entryS, entryM));

        assertEquals("M", result.getRecommendedSize());
        assertEquals(MatchResult.MatchQuality.EXACT, result.getQuality());
        assertEquals(1.0, result.getConfidenceScore(), DELTA);
    }

    @Test
    void given_multipleEntries_when_differentScores_then_picksHighestScore() {
        // Entry XS: no dimensions match (score 0.0 → CLOSEST)
        // Entry L: all dimensions match (score 1.0 → EXACT)
        SizeEntry entryXS = entryWithChestWaistHip("XS", 50, 60, 40, 50, 55, 65);
        SizeEntry entryL = entryWithChestWaistHip("L", 85, 95, 70, 80, 90, 100);

        MatchResult result = matcher.match(normalProfile, List.of(entryXS, entryL));

        assertEquals("L", result.getRecommendedSize());
        assertEquals(MatchResult.MatchQuality.EXACT, result.getQuality());
        assertEquals(1.0, result.getConfidenceScore(), DELTA);
    }

    // -------------------------------------------------------------------------
    // Tie-breaking: when two sizes both fully contain the body, the better-centred one wins
    // -------------------------------------------------------------------------

    @Test
    void given_twoContainingEntries_when_scoresTie_then_prefersBetterCentredRanges() {
        // normalProfile: chest=90, waist=75, hip=95
        // Both entries contain all three, so overallScore is 1.0 for each.
        // entryWide's ranges sit off to one side; entryCentred is centred on the body.
        SizeEntry entryWide = entryWithChestWaistHip("WIDE", 85, 110, 70, 95, 90, 115);
        SizeEntry entryCentred = entryWithChestWaistHip("CENTRED", 86, 94, 71, 79, 91, 99);

        MatchResult result = matcher.match(normalProfile, List.of(entryWide, entryCentred));

        assertEquals("CENTRED", result.getRecommendedSize());
        assertEquals(MatchResult.MatchQuality.EXACT, result.getQuality());
    }

    // -------------------------------------------------------------------------
    // OUT_OF_RANGE BodyProfile — confidence capped at 0.5
    // -------------------------------------------------------------------------

    @Test
    void given_outOfRangeBodyProfile_when_perfectMatch_then_confidenceCappedAt0Point5() {
        // outOfRangeProfile: chest=100, waist=90, hip=105 — all within range below
        SizeEntry entry = entryWithChestWaistHip("XL", 95, 105, 85, 95, 100, 110);

        MatchResult result = matcher.match(outOfRangeProfile, List.of(entry));

        // Score would be 1.0 (EXACT) but is capped to 0.5 due to OUT_OF_RANGE profile
        assertEquals(0.5, result.getConfidenceScore(), DELTA);
        // Quality is preserved from before the cap (EXACT)
        assertEquals(MatchResult.MatchQuality.EXACT, result.getQuality());
    }

    // -------------------------------------------------------------------------
    // Entry with all null measurements — must be skipped
    // -------------------------------------------------------------------------

    @Test
    void given_entryWithAllNullMeasurements_when_match_then_entryIsSkippedAndReturnsNoMatch() {
        // A SizeEntry with no defined measurement bounds → availableDimensions = 0 → filtered out
        SizeEntry nullEntry = new SizeEntry();
        nullEntry.setId(UUID.randomUUID());
        nullEntry.setSizeLabel("NULL");
        // All measurement fields intentionally left null

        MatchResult result = matcher.match(normalProfile, List.of(nullEntry));

        assertEquals(MatchResult.MatchQuality.NO_MATCH, result.getQuality());
    }

    // -------------------------------------------------------------------------
    // Helper factory methods
    // -------------------------------------------------------------------------

    /** Creates a SizeEntry with chest, waist, and hip bounds defined. */
    private SizeEntry entryWithChestWaistHip(String label,
                                             double chestMin, double chestMax,
                                             double waistMin, double waistMax,
                                             double hipMin, double hipMax) {
        SizeEntry e = new SizeEntry();
        e.setId(UUID.randomUUID());
        e.setSizeLabel(label);
        e.setChestMin(BigDecimal.valueOf(chestMin));
        e.setChestMax(BigDecimal.valueOf(chestMax));
        e.setWaistMin(BigDecimal.valueOf(waistMin));
        e.setWaistMax(BigDecimal.valueOf(waistMax));
        e.setHipMin(BigDecimal.valueOf(hipMin));
        e.setHipMax(BigDecimal.valueOf(hipMax));
        return e;
    }

    /** Creates a SizeEntry with only chest bounds defined. */
    private SizeEntry entryWithChestOnly(String label, double chestMin, double chestMax) {
        SizeEntry e = new SizeEntry();
        e.setId(UUID.randomUUID());
        e.setSizeLabel(label);
        e.setChestMin(BigDecimal.valueOf(chestMin));
        e.setChestMax(BigDecimal.valueOf(chestMax));
        return e;
    }

    /** Creates a SizeEntry with only waist bounds defined. */
    private SizeEntry entryWithWaistOnly(String label, double waistMin, double waistMax) {
        SizeEntry e = new SizeEntry();
        e.setId(UUID.randomUUID());
        e.setSizeLabel(label);
        e.setWaistMin(BigDecimal.valueOf(waistMin));
        e.setWaistMax(BigDecimal.valueOf(waistMax));
        return e;
    }
}
