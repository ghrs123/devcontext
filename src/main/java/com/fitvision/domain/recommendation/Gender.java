package com.fitvision.domain.recommendation;

public enum Gender {
    MALE(1.0),
    FEMALE(0.0),
    UNISEX(0.5);

    private final double genderFactor;

    Gender(double genderFactor) {
        this.genderFactor = genderFactor;
    }

    public double getGenderFactor() {
        return genderFactor;
    }
}
