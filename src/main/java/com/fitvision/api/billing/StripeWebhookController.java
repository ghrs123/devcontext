package com.fitvision.api.billing;

import com.fitvision.domain.billing.Plan;
import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/billing")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    // Maps Stripe price IDs to FitVision plan names
    private final Map<String, String> priceIdToPlan;
    private final String webhookSecret;
    private final StoreRepository storeRepository;

    public StripeWebhookController(
            StoreRepository storeRepository,
            @Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${stripe.prices.starter:}") String starterPriceId,
            @Value("${stripe.prices.pro:}") String proPriceId,
            @Value("${stripe.prices.team:}") String teamPriceId) {
        this.storeRepository = storeRepository;
        this.webhookSecret = webhookSecret;
        this.priceIdToPlan = Map.of(
                starterPriceId, Plan.STARTER.name(),
                proPriceId,     Plan.PRO.name(),
                teamPriceId,    Plan.TEAM.name()
        );
    }

    @PostMapping("/webhooks")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (webhookSecret.isBlank()) {
            log.warn("Stripe webhook received but webhook secret is not configured — rejecting.");
            return ResponseEntity.badRequest().build();
        }

        if (sigHeader == null) {
            log.warn("Stripe webhook received without Stripe-Signature header — rejecting.");
            return ResponseEntity.badRequest().build();
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException ex) {
            log.warn("Stripe webhook signature validation failed: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }

        log.info("Stripe webhook received: type={} id={}", event.getType(), event.getId());

        switch (event.getType()) {
            case "customer.subscription.created",
                 "customer.subscription.updated" -> handleSubscriptionUpsert(event, false);
            case "customer.subscription.deleted"  -> handleSubscriptionUpsert(event, true);
            case "invoice.payment_failed"         -> handlePaymentFailed(event);
            default -> log.debug("Stripe webhook event not handled: {}", event.getType());
        }

        return ResponseEntity.ok().build();
    }

    private void handleSubscriptionUpsert(Event event, boolean deleted) {
        Subscription subscription = deserializeSubscription(event).orElse(null);
        if (subscription == null) return;

        String customerId = subscription.getCustomer();
        Store store = storeRepository.findByStripeCustomerId(customerId).orElse(null);
        if (store == null) {
            log.warn("Stripe webhook: no store found for customerId={}", customerId);
            return;
        }

        if (deleted) {
            store.setPlan(Plan.FREE.name());
            store.setSubscriptionStatus("inactive");
            store.setStripeSubscriptionId(null);
            store.setStripePriceId(null);
            store.setSubscriptionCurrentPeriodEnd(null);
        } else {
            String priceId = subscription.getItems().getData().isEmpty()
                    ? null
                    : subscription.getItems().getData().get(0).getPrice().getId();

            String plan = priceId != null ? priceIdToPlan.getOrDefault(priceId, Plan.FREE.name()) : Plan.FREE.name();
            store.setPlan(plan);
            store.setStripeSubscriptionId(subscription.getId());
            store.setStripePriceId(priceId);
            store.setSubscriptionStatus(subscription.getStatus());

            if (subscription.getCurrentPeriodEnd() != null) {
                store.setSubscriptionCurrentPeriodEnd(
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()), ZoneOffset.UTC));
            }
        }

        storeRepository.save(store);
        log.info("Stripe webhook: updated storeId={} plan={} status={}", store.getId(), store.getPlan(), store.getSubscriptionStatus());
    }

    private void handlePaymentFailed(Event event) {
        // invoice.payment_failed doesn't contain a Subscription object directly.
        // We extract the subscription ID from the raw data.
        try {
            com.stripe.model.Invoice invoice = (com.stripe.model.Invoice)
                    deserializeEventObject(event).orElse(null);
            if (invoice == null) return;

            String subscriptionId = invoice.getSubscription();
            if (subscriptionId == null) return;

            storeRepository.findByStripeSubscriptionId(subscriptionId).ifPresent(store -> {
                store.setSubscriptionStatus("past_due");
                storeRepository.save(store);
                log.warn("Stripe invoice.payment_failed: storeId={} subscriptionId={}", store.getId(), subscriptionId);
            });
        } catch (Exception ex) {
            log.error("Failed to process invoice.payment_failed event: {}", ex.getMessage());
        }
    }

    private Optional<Subscription> deserializeSubscription(Event event) {
        return deserializeEventObject(event)
                .filter(obj -> obj instanceof Subscription)
                .map(obj -> (Subscription) obj);
    }

    /**
     * Deserializes the event's data object, tolerating a mismatch between the Stripe
     * account's API version and the one {@code stripe-java} is pinned to.
     *
     * <p>{@link EventDataObjectDeserializer#getObject()} returns empty whenever the event
     * was rendered with a different API version, which would otherwise make every webhook
     * a silent no-op. The fields this controller reads (subscription id, customer, status,
     * {@code items[].price.id}; invoice subscription id) are stable across versions, so we
     * fall back to {@link EventDataObjectDeserializer#deserializeUnsafe()}.
     */
    private Optional<StripeObject> deserializeEventObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> object = deserializer.getObject();
        if (object.isPresent()) {
            return object;
        }
        try {
            return Optional.ofNullable(deserializer.deserializeUnsafe());
        } catch (Exception ex) {
            log.error("Failed to deserialize Stripe event {} (type={}): {}",
                    event.getId(), event.getType(), ex.getMessage());
            return Optional.empty();
        }
    }
}
