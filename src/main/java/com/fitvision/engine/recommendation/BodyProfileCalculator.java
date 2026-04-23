package com.fitvision.engine.recommendation;

import com.fitvision.domain.recommendation.Gender;
import com.fitvision.shared.exception.InvalidBodyMeasurementException;
import org.springframework.stereotype.Service;

/**
 * Stateless service that derives anthropometric estimates from buyer-supplied inputs.
 *
 * Formulas used:
 * - BMI: Quetelet index (weight_kg / height_m²)
 * - Body fat: Deurenberg et al. (1991) — validated for adults
 * - Chest estimate: lean mass proxy adapted from anthropometric tables
 * - Waist estimate: YMCA protocol approximation
 * - Hip estimate: chest-to-hip ratio adjusted by gender
 */
@Service
public class BodyProfileCalculator {

    private static final double MIN_HEIGHT_CM = 50.0;
    private static final double MAX_HEIGHT_CM = 250.0;
    private static final double MIN_WEIGHT_KG = 20.0;
    private static final double MAX_WEIGHT_KG = 300.0;

    private static final int DEFAULT_AGE = 30;

    // Deurenberg formula constants
    private static final double DEURENBERG_BMI_FACTOR = 1.20;
    private static final double DEURENBERG_AGE_FACTOR = 0.23;
    private static final double DEURENBERG_GENDER_FACTOR = 10.8;
    private static final double DEURENBERG_CONSTANT = 5.4;

    // Chest estimate constants
    private static final double CHEST_BASE_CM = 85.0;
    private static final double CHEST_LEAN_MASS_FACTOR = 0.4;
    private static final double CHEST_BMI_FACTOR = 0.5;

    // Waist estimate constants (YMCA approximation)
    private static final double WAIST_WEIGHT_FACTOR = 0.74;
    private static final double WAIST_HEIGHT_FACTOR = 0.18;
    private static final double WAIST_CONSTANT = 28.0;

    // Hip estimate constants
    private static final double HIP_CHEST_MULTIPLIER = 1.05;
    private static final double HIP_FEMALE_ADJUSTMENT = 1.08;
    private static final double HIP_MALE_ADJUSTMENT = 1.0;
    private static final double HIP_UNISEX_ADJUSTMENT = 1.04;

    /**
     * Computes a BodyProfile from buyer-supplied measurements.
     *
     * @param heightCm height in centimetres (50–250)
     * @param weightKg weight in kilograms (20–300)
     * @param gender   gender for Deurenberg formula; must not be null
     * @param age      age in years; null defaults to 30
     * @throws InvalidBodyMeasurementException if height or weight are outside valid ranges
     */
    public BodyProfile calculate(double heightCm, double weightKg, Gender gender, Integer age) {
        validateInputs(heightCm, weightKg);

        int effectiveAge = age != null ? age : DEFAULT_AGE;

        double bmi = computeBmi(heightCm, weightKg);
        double bodyFatPct = computeBodyFatPct(bmi, effectiveAge, gender);
        double leanMassKg = computeLeanMassKg(weightKg, bodyFatPct);
        double chestCm = round1dp(computeChestCm(leanMassKg, bmi));
        double waistCm = round1dp(computeWaistCm(weightKg, heightCm));
        double hipCm = round1dp(computeHipCm(chestCm, gender));

        return new BodyProfile(heightCm, weightKg, gender, effectiveAge,
                round1dp(bmi), round1dp(bodyFatPct), chestCm, waistCm, hipCm);
    }

    private void validateInputs(double heightCm, double weightKg) {
        if (heightCm < MIN_HEIGHT_CM || heightCm > MAX_HEIGHT_CM) {
            throw new InvalidBodyMeasurementException(
                    "Height must be between " + (int) MIN_HEIGHT_CM + " and " + (int) MAX_HEIGHT_CM + " cm, got: " + heightCm);
        }
        if (weightKg < MIN_WEIGHT_KG || weightKg > MAX_WEIGHT_KG) {
            throw new InvalidBodyMeasurementException(
                    "Weight must be between " + (int) MIN_WEIGHT_KG + " and " + (int) MAX_WEIGHT_KG + " kg, got: " + weightKg);
        }
    }

    /** BMI = weight_kg / (height_m)² */
    private double computeBmi(double heightCm, double weightKg) {
        double heightM = heightCm / 100.0;
        return weightKg / (heightM * heightM);
    }

    /**
     * Deurenberg (1991) body fat estimation.
     * body_fat_pct = (1.20 × bmi) + (0.23 × age) - (10.8 × gender_factor) - 5.4
     */
    private double computeBodyFatPct(double bmi, int age, Gender gender) {
        return (DEURENBERG_BMI_FACTOR * bmi)
                + (DEURENBERG_AGE_FACTOR * age)
                - (DEURENBERG_GENDER_FACTOR * gender.getGenderFactor())
                - DEURENBERG_CONSTANT;
    }

    /** lean_mass_kg = weight_kg × (1 - body_fat_pct / 100) */
    private double computeLeanMassKg(double weightKg, double bodyFatPct) {
        return weightKg * (1.0 - bodyFatPct / 100.0);
    }

    /** chest_cm = 85 + (lean_mass_kg × 0.4) + (bmi × 0.5) */
    private double computeChestCm(double leanMassKg, double bmi) {
        return CHEST_BASE_CM + (leanMassKg * CHEST_LEAN_MASS_FACTOR) + (bmi * CHEST_BMI_FACTOR);
    }

    /** waist_cm = (weight_kg × 0.74) + (height_cm × 0.18) - 28 — YMCA approximation */
    private double computeWaistCm(double weightKg, double heightCm) {
        return (WAIST_WEIGHT_FACTOR * weightKg) + (WAIST_HEIGHT_FACTOR * heightCm) - WAIST_CONSTANT;
    }

    /** hip_cm = chest_cm × 1.05 × gender_adjustment */
    private double computeHipCm(double chestCm, Gender gender) {
        double genderAdjustment = switch (gender) {
            case FEMALE -> HIP_FEMALE_ADJUSTMENT;
            case MALE -> HIP_MALE_ADJUSTMENT;
            case UNISEX -> HIP_UNISEX_ADJUSTMENT;
        };
        return chestCm * HIP_CHEST_MULTIPLIER * genderAdjustment;
    }

    private double round1dp(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
