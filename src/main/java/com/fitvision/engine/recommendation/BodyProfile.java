package com.fitvision.engine.recommendation;

import com.fitvision.domain.recommendation.Gender;

public final class BodyProfile {

    public enum RangeStatus { NORMAL, OUT_OF_RANGE }

    private static final double BMI_MIN = 15.0;
    private static final double BMI_MAX = 45.0;

    private final double heightCm;
    private final double weightKg;
    private final Gender gender;
    private final int age;
    private final double bmi;
    private final double bodyFatPct;
    private final double estimatedChestCm;
    private final double estimatedWaistCm;
    private final double estimatedHipCm;
    private final RangeStatus rangeStatus;

    BodyProfile(double heightCm, double weightKg, Gender gender, int age,
                double bmi, double bodyFatPct,
                double estimatedChestCm, double estimatedWaistCm, double estimatedHipCm) {
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.gender = gender;
        this.age = age;
        this.bmi = bmi;
        this.bodyFatPct = bodyFatPct;
        this.estimatedChestCm = estimatedChestCm;
        this.estimatedWaistCm = estimatedWaistCm;
        this.estimatedHipCm = estimatedHipCm;
        this.rangeStatus = (bmi < BMI_MIN || bmi > BMI_MAX) ? RangeStatus.OUT_OF_RANGE : RangeStatus.NORMAL;
    }

    public double getHeightCm() { return heightCm; }
    public double getWeightKg() { return weightKg; }
    public Gender getGender() { return gender; }
    public int getAge() { return age; }
    public double getBmi() { return bmi; }
    public double getBodyFatPct() { return bodyFatPct; }
    public double getEstimatedChestCm() { return estimatedChestCm; }
    public double getEstimatedWaistCm() { return estimatedWaistCm; }
    public double getEstimatedHipCm() { return estimatedHipCm; }
    public RangeStatus getRangeStatus() { return rangeStatus; }
    public boolean isOutOfRange() { return rangeStatus == RangeStatus.OUT_OF_RANGE; }

    /** Safe for INFO-level logging — does not expose raw measurements. */
    @Override
    public String toString() {
        return "BodyProfile{bmi=" + bmi + ", rangeStatus=" + rangeStatus + ", gender=" + gender + "}";
    }
}
