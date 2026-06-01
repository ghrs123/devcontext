package com.fitvision.api.dashboard.billing;

import com.fitvision.domain.billing.Plan;
import com.fitvision.domain.billing.StripeService;
import org.springframework.beans.factory.annotation.Value;
import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard/v1/billing")
@Tag(name = "Billing")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final StripeService stripeService;

    @Value("${stripe.prices.starter:}")
    private String starterPriceId;

    @Value("${stripe.prices.pro:}")
    private String proPriceId;

    @Value("${stripe.prices.team:}")
    private String teamPriceId;

    @Value("${fitvision.dashboard.url}")
    private String dashboardUrl;

    public BillingController(StoreRepository storeRepository,
                             ProductRepository productRepository,
                             StripeService stripeService) {
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.stripeService = stripeService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<BillingStatusResponse>> getStatus() {
        UUID tenantId = TenantContext.get();
        Store store = requireStore(tenantId);
        Plan plan = Plan.from(store.getPlan());

        long productsUsed = productRepository.countByTenantIdAndDeletedAtIsNull(tenantId);
        int recommendationsUsed = store.getRecommendationsCountCurrentMonth() != null
                ? store.getRecommendationsCountCurrentMonth() : 0;

        BillingStatusResponse response = new BillingStatusResponse(
                plan.name(),
                store.getSubscriptionStatus(),
                store.getSubscriptionCurrentPeriodEnd(),
                productsUsed,
                plan.isUnlimited() ? Integer.MAX_VALUE : plan.getMaxProducts(),
                recommendationsUsed,
                plan.isUnlimited() ? Integer.MAX_VALUE : plan.getMaxRecommendationsPerMonth()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> createCheckout(
            @RequestBody Map<String, String> body) {

        UUID tenantId = TenantContext.get();
        Store store = requireStore(tenantId);

        // Accept either a direct priceId or a plan name (STARTER/PRO/TEAM).
        String priceId = body.getOrDefault("priceId", "").trim();
        if (priceId.isBlank()) {
            String planName = body.getOrDefault("plan", "").trim().toUpperCase();
            priceId = switch (planName) {
                case "STARTER" -> starterPriceId;
                case "PRO"     -> proPriceId;
                case "TEAM"    -> teamPriceId;
                default -> "";
            };
        }

        if (priceId.isBlank()) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR,
                    "A valid 'plan' (STARTER, PRO, TEAM) or 'priceId' is required. Stripe may not be configured.");
        }

        String customerId = ensureStripeCustomer(store);
        String successUrl = dashboardUrl + "/settings?billing=success";
        String cancelUrl  = dashboardUrl + "/settings?billing=cancelled";

        String checkoutUrl = stripeService.createCheckoutSession(customerId, priceId, successUrl, cancelUrl);
        log.info("Billing checkout session created: storeId={}", tenantId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("checkoutUrl", checkoutUrl)));
    }

    @PostMapping("/portal")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> createPortalSession() {
        UUID tenantId = TenantContext.get();
        Store store = requireStore(tenantId);

        String customerId = ensureStripeCustomer(store);
        String returnUrl = dashboardUrl + "/settings";

        String portalUrl = stripeService.createBillingPortalSession(customerId, returnUrl);
        log.info("Billing portal session created: storeId={}", tenantId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("portalUrl", portalUrl)));
    }

    private String ensureStripeCustomer(Store store) {
        if (store.getStripeCustomerId() != null) {
            return store.getStripeCustomerId();
        }
        String customerId = stripeService.createCustomer(store.getEmail(), store.getName());
        store.setStripeCustomerId(customerId);
        store.setUpdatedAt(LocalDateTime.now());
        storeRepository.save(store);
        return customerId;
    }

    private Store requireStore(UUID tenantId) {
        return storeRepository.findById(tenantId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.STORE_NOT_FOUND, "Store not found"));
    }
}
