package com.fitvision.api.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record StoreAdminView(
        UUID id,
        String name,
        String email,
        String plan,
        String role,
        String status,
        String platform,
        LocalDateTime createdAt,
        long totalProducts,
        long totalRecommendations,
        LocalDateTime lastRecommendationAt,
        String subscriptionStatus,
        String stripeCustomerIdMasked,
        LocalDateTime subscriptionCurrentPeriodEnd
) {
}
