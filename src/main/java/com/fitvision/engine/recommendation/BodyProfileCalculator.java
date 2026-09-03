package com.fitvision.engine.recommendation;

import com.fitvision.domain.recommendation.Gender;
import com.fitvision.shared.exception.InvalidBodyMeasurementException;
import org.springframework.stereotype.Service;

/**
 * Stateless service that derives anthropometric estimates from buyer-supplied inputs
 * (height, weight, gender, age).
 *
 * <p>Model:
 * <ul>
 *   <li><b>BMI</b> — Quetelet index (weight_kg / height_m²).</li>
 *   <li><b>Body fat %</b> — Deurenberg et al. (1991), validated for adults.</li>
 *   <li><b>Chest / waist / hip circumference</b> — each is a sex-specific fraction of
 *       stature at a reference BMI of 22, scaled by {@code sqrt(BMI / 22)}. A trunk
 *       modelled as a cylinder has circumference proportional to the square root of its
 *       cross-sectional area, and area is proportional to mass at fixed height, so girth
 *       scales with the square root of BMI. Waist gets an extra adjustment for body-fat
 *       distribution (a higher-fat person at the same BMI carries more at the waist).</li>
 * </ul>
 * The reference ratios are drawn from adult anthropometric norms (a ~175&nbsp;cm,
 * BMI&nbsp;22 man measures roughly 93/80/91&nbsp;cm chest/waist/hip). These are estimates
 * from coarse inputs — {@link SizeChartMatcher} treats them as such when scoring.
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

    // Circumference model
    private static final double REFERENCE_BMI = 22.0;
    private static final double BMI_SCALE_FLOOR = 15.0;
    private static final double BMI_SCALE_CEILING = 45.0;

    // Circumference-to-stature ratios at REFERENCE_BMI (adult anthropometric norms).
    private static final double CHEST_RATIO_MALE = 0.530;
    private static final double CHEST_RATIO_FEMALE = 0.520;
    private static final double WAIST_RATIO_MALE = 0.455;
    private static final double WAIST_RATIO_FEMALE = 0.420;
    private static final double HIP_RATIO_MALE = 0.520;
    private static final double HIP_RATIO_FEMALE = 0.565;

    // Waist adjustment for body-fat distribution.
    private static final double REF_BODY_FAT_MALE = 18.0;
    private static final double REF_BODY_FAT_FEMALE = 26.0;
    private static final double WAIST_FAT_SENSITIVITY = 0.6;
    private static final double WAIST_FAT_ADJ_MIN = -0.06;
    private static final double WAIST_FAT_ADJ_MAX = 0.12;

    /**
     * Computes a BodyProfile from buyer-supplied measurements.
     *
     * @param heightCm height in centimetres (50–250)
     * @param weightKg weight in kilograms (20–300)
     * @param gender   gender for the Deurenberg formula and sex-specific ratios; must not be null
     * @param age      age in years; null defaults to 30
     * @throws InvalidBodyMeasurementException if height or weight are outside valid ranges
     */
    public BodyProfile calculate(double heightCm, double weightKg, Gender gender, Integer age) {
        validateInputs(heightCm, weightKg);

        int effectiveAge = age != null ? age : DEFAULT_AGE;

        double bmi = computeBmi(heightCm, weightKg);
        double bodyFatPct = computeBodyFatPct(bmi, effectiveAge, gender);
        double bmiScale = Math.sqrt(clamp(bmi, BMI_SCALE_FLOOR, BMI_SCALE_CEILING) / REFERENCE_BMI);

        double chestCm = round1dp(chestRatio(gender) * heightCm * bmiScale);
        double hipCm = round1dp(hipRatio(gender) * heightCm * bmiScale);
        double waistCm = round1dp(
                waistRatio(gender) * heightCm * bmiScale * (1.0 + waistFatAdjustment(bodyFatPct, gender)));

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

    private double chestRatio(Gender gender) {
        return blendByGender(gender, CHEST_RATIO_MALE, CHEST_RATIO_FEMALE);
    }

    private double waistRatio(Gender gender) {
        return blendByGender(gender, WAIST_RATIO_MALE, WAIST_RATIO_FEMALE);
    }

    private double hipRatio(Gender gender) {
        return blendByGender(gender, HIP_RATIO_MALE, HIP_RATIO_FEMALE);
    }

    /** Extra waist girth for a body-fat percentage above the sex reference (capped). */
    private double waistFatAdjustment(double bodyFatPct, Gender gender) {
        double refFat = blendByGender(gender, REF_BODY_FAT_MALE, REF_BODY_FAT_FEMALE);
        double raw = WAIST_FAT_SENSITIVITY * (bodyFatPct - refFat) / 100.0;
        return clamp(raw, WAIST_FAT_ADJ_MIN, WAIST_FAT_ADJ_MAX);
    }

    /** MALE → male value, FEMALE → female value, UNISEX → midpoint. */
    private double blendByGender(Gender gender, double maleValue, double femaleValue) {
        return switch (gender) {
            case MALE -> maleValue;
            case FEMALE -> femaleValue;
            case UNISEX -> (maleValue + femaleValue) / 2.0;
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round1dp(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
