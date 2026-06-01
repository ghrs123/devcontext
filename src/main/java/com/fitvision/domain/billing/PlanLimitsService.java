package com.fitvision.domain.billing;

import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PlanLimitsService {

    private static final Logger log = LoggerFactory.getLogger(PlanLimitsService.class);

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;

    public PlanLimitsService(StoreRepository storeRepository, ProductRepository productRepository) {
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public void checkProductLimit(UUID tenantId) {
        Store store = requireStore(tenantId);
        Plan plan = Plan.from(store.getPlan());
        if (plan.isUnlimited()) return;

        long current = productRepository.countByTenantIdAndDeletedAtIsNull(tenantId);
        if (current >= plan.getMaxProducts()) {
            throw new PlanLimitException(
                    "You've reached the " + plan.getMaxProducts() + " product limit on the "
                    + plan.displayName() + " plan. Upgrade to add more products.");
        }
    }

    @Transactional
    public void checkRecommendationLimit(UUID tenantId) {
        Store store = requireStore(tenantId);
        Plan plan = Plan.from(store.getPlan());
        if (plan.isUnlimited()) return;

        resetCounterIfNewMonth(store, tenantId);
        // Reload after potential reset
        store = requireStore(tenantId);

        int used = store.getRecommendationsCountCurrentMonth() != null
                ? store.getRecommendationsCountCurrentMonth() : 0;

        if (used >= plan.getMaxRecommendationsPerMonth()) {
            throw new PlanLimitException(
                    "Monthly recommendation limit of " + plan.getMaxRecommendationsPerMonth()
                    + " reached on the " + plan.displayName() + " plan. Upgrade to continue.");
        }
    }

    @Transactional
    public void incrementRecommendationCount(UUID tenantId) {
        storeRepository.incrementRecommendationCount(tenantId);
    }

    public Plan getPlanForStore(UUID tenantId) {
        Store store = requireStore(tenantId);
        return Plan.from(store.getPlan());
    }

    private void resetCounterIfNewMonth(Store store, UUID tenantId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime resetAt = store.getRecommendationsCountResetAt();

        boolean needsReset = resetAt == null
                || resetAt.getYear() != now.getYear()
                || resetAt.getMonthValue() != now.getMonthValue();

        if (needsReset) {
            log.info("Resetting monthly recommendation counter for tenantId={}", tenantId);
            storeRepository.resetRecommendationCount(tenantId, now);
        }
    }

    private Store requireStore(UUID tenantId) {
        return storeRepository.findById(tenantId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.STORE_NOT_FOUND, "Store not found"));
    }
}
