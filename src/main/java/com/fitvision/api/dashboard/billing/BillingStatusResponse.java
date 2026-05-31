package com.fitvision.api.dashboard.billing;

import java.time.LocalDateTime;

public record BillingStatusResponse(
        String plan,
        String subscriptionStatus,
        LocalDateTime currentPeriodEnd,
        long productsUsed,
        int productsLimit,
        int recommendationsUsed,
        int recommendationsLimit
) {
}
