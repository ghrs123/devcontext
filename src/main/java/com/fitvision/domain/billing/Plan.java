package com.fitvision.domain.billing;

public enum Plan {

    FREE(2, 100),
    STARTER(10, 5_000),
    PRO(50, 25_000),
    TEAM(Integer.MAX_VALUE, Integer.MAX_VALUE);

    private final int maxProducts;
    private final int maxRecommendationsPerMonth;

    Plan(int maxProducts, int maxRecommendationsPerMonth) {
        this.maxProducts = maxProducts;
        this.maxRecommendationsPerMonth = maxRecommendationsPerMonth;
    }

    public int getMaxProducts() {
        return maxProducts;
    }

    public int getMaxRecommendationsPerMonth() {
        return maxRecommendationsPerMonth;
    }

    public boolean isUnlimited() {
        return this == TEAM;
    }

    public static Plan from(String value) {
        if (value == null || value.isBlank()) return FREE;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return FREE;
        }
    }

    public String displayName() {
        return switch (this) {
            case FREE    -> "Free";
            case STARTER -> "Starter";
            case PRO     -> "Pro";
            case TEAM    -> "Team";
        };
    }
}
