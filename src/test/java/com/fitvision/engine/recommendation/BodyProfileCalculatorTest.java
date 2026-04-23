package com.fitvision.engine.recommendation;

import com.fitvision.domain.recommendation.Gender;
import com.fitvision.shared.exception.InvalidBodyMeasurementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BodyProfileCalculator.
 * No Spring context — plain JUnit 5.
 */
class BodyProfileCalculatorTest {

    private static final double DELTA = 0.5;

    private BodyProfileCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new BodyProfileCalculator();
    }

    // -------------------------------------------------------------------------
    // BMI and formula verification
    // -------------------------------------------------------------------------

    @Test
    void given_averageMale_when_calculate_then_bmiIsCorrect() {
        // BMI = 75 / (1.75)² = 24.49
        BodyProfile profile = calculator.calculate(175, 75, Gender.MALE, 30);

        assertEquals(24.5, profile.getBmi(), DELTA);
        assertEquals(Gender.MALE, profile.getGender());
        assertEquals(30, profile.getAge());
        assertEquals(BodyProfile.RangeStatus.NORMAL, profile.getRangeStatus());
    }

    @Test
    void given_averageMale_when_calculate_then_chestWaistHipWithinExpectedRange() {
        // chest ≈ 85 + (leanMass × 0.4) + (bmi × 0.5) ≈ 121.2
        // waist ≈ (75 × 0.74) + (175 × 0.18) - 28 = 59.0
        // hip   ≈ chest × 1.05 × 1.0 (MALE) ≈ 127.3
        BodyProfile profile = calculator.calculate(175, 75, Gender.MALE, 30);

        assertEquals(121.2, profile.getEstimatedChestCm(), DELTA);
        assertEquals(59.0, profile.getEstimatedWaistCm(), DELTA);
        assertEquals(127.3, profile.getEstimatedHipCm(), DELTA);
    }

    @Test
    void given_averageFemale_when_calculate_then_genderFactorAppliedCorrectly() {
        // Female gender_factor = 0.0 → body fat formula adds 0 instead of 10.8
        // So female body fat pct should be ~10.8 percentage points HIGHER than equivalent male
        BodyProfile female = calculator.calculate(165, 60, Gender.FEMALE, 30);
        BodyProfile male = calculator.calculate(165, 60, Gender.MALE, 30);

        // Female body fat must be substantially higher than male (Deurenberg accounts for this)
        assertTrue(female.getBodyFatPct() > male.getBodyFatPct() + 8.0,
                "Female body fat should be significantly higher than male for same height/weight");

        // Female BMI ≈ 60 / (1.65)² = 22.0
        assertEquals(22.0, female.getBmi(), DELTA);
    }

    @Test
    void given_averageFemale_when_calculate_then_hipIsAdjustedForFemaleGender() {
        // Female hip = chest × 1.05 × 1.08 — larger than MALE (× 1.0) or UNISEX (× 1.04)
        BodyProfile female = calculator.calculate(165, 60, Gender.FEMALE, 30);
        BodyProfile male = calculator.calculate(165, 60, Gender.MALE, 30);

        assertTrue(female.getEstimatedHipCm() > male.getEstimatedHipCm(),
                "Female hip estimate should be larger than male for same measurements");
    }

    @Test
    void given_unisexGender_when_calculate_then_genderFactorIsHalf() {
        // UNISEX gender factor = 0.5 → body fat should be between MALE and FEMALE values
        BodyProfile male = calculator.calculate(175, 75, Gender.MALE, 30);
        BodyProfile female = calculator.calculate(175, 75, Gender.FEMALE, 30);
        BodyProfile unisex = calculator.calculate(175, 75, Gender.UNISEX, 30);

        assertTrue(unisex.getBodyFatPct() > male.getBodyFatPct(),
                "UNISEX body fat should be higher than MALE");
        assertTrue(unisex.getBodyFatPct() < female.getBodyFatPct(),
                "UNISEX body fat should be lower than FEMALE");
    }

    // -------------------------------------------------------------------------
    // Boundary inputs — must not throw
    // -------------------------------------------------------------------------

    @Test
    void given_minimumValidInput_when_calculate_then_doesNotThrow() {
        assertDoesNotThrow(() -> calculator.calculate(50, 20, Gender.MALE, 25));
    }

    @Test
    void given_maximumValidInput_when_calculate_then_doesNotThrow() {
        assertDoesNotThrow(() -> calculator.calculate(250, 300, Gender.FEMALE, 60));
    }

    // -------------------------------------------------------------------------
    // Null age — must use default of 30
    // -------------------------------------------------------------------------

    @Test
    void given_nullAge_when_calculate_then_usesDefaultAgeAndDoesNotThrow() {
        BodyProfile profileWithNull = calculator.calculate(175, 75, Gender.MALE, null);
        BodyProfile profileWithDefault = calculator.calculate(175, 75, Gender.MALE, 30);

        assertNotNull(profileWithNull);
        assertEquals(30, profileWithNull.getAge());
        assertEquals(profileWithDefault.getBmi(), profileWithNull.getBmi(), 0.001);
        assertEquals(profileWithDefault.getBodyFatPct(), profileWithNull.getBodyFatPct(), 0.001);
    }

    // -------------------------------------------------------------------------
    // Invalid inputs — must throw InvalidBodyMeasurementException
    // -------------------------------------------------------------------------

    @Test
    void given_heightBelowMinimum_when_calculate_then_throwsInvalidBodyMeasurementException() {
        InvalidBodyMeasurementException ex = assertThrows(
                InvalidBodyMeasurementException.class,
                () -> calculator.calculate(49, 70, Gender.MALE, 30));

        assertTrue(ex.getMessage().contains("49"),
                "Exception message should reference the invalid height value");
    }

    @Test
    void given_weightBelowMinimum_when_calculate_then_throwsInvalidBodyMeasurementException() {
        InvalidBodyMeasurementException ex = assertThrows(
                InvalidBodyMeasurementException.class,
                () -> calculator.calculate(170, 19, Gender.FEMALE, 25));

        assertTrue(ex.getMessage().contains("19"),
                "Exception message should reference the invalid weight value");
    }

    @Test
    void given_heightAboveMaximum_when_calculate_then_throwsInvalidBodyMeasurementException() {
        assertThrows(InvalidBodyMeasurementException.class,
                () -> calculator.calculate(251, 80, Gender.MALE, 30));
    }

    @Test
    void given_weightAboveMaximum_when_calculate_then_throwsInvalidBodyMeasurementException() {
        assertThrows(InvalidBodyMeasurementException.class,
                () -> calculator.calculate(170, 301, Gender.MALE, 30));
    }

    // -------------------------------------------------------------------------
    // Range status
    // -------------------------------------------------------------------------

    @Test
    void given_normalBmiInput_when_calculate_then_rangeStatusIsNormal() {
        // 175cm, 75kg → BMI ≈ 24.5 (within 15–45)
        BodyProfile profile = calculator.calculate(175, 75, Gender.MALE, 30);
        assertEquals(BodyProfile.RangeStatus.NORMAL, profile.getRangeStatus());
        assertFalse(profile.isOutOfRange());
    }
}
