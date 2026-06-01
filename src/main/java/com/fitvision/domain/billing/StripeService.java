package com.fitvision.domain.billing;

import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    private final String secretKey;

    public StripeService(@Value("${stripe.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
        if (!secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    public String createCustomer(String email, String name) {
        requireConfigured();
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setEmail(email)
                    .setName(name)
                    .build();
            Customer customer = Customer.create(params);
            return customer.getId();
        } catch (StripeException ex) {
            log.error("Stripe createCustomer failed: {}", ex.getMessage());
            throw new FitVisionException(ErrorCode.STRIPE_ERROR, "Failed to create billing customer: " + ex.getMessage());
        }
    }

    public Subscription createSubscription(String customerId, String priceId) {
        requireConfigured();
        try {
            SubscriptionCreateParams params = SubscriptionCreateParams.builder()
                    .setCustomer(customerId)
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(priceId)
                            .build())
                    .build();
            return Subscription.create(params);
        } catch (StripeException ex) {
            log.error("Stripe createSubscription failed: {}", ex.getMessage());
            throw new FitVisionException(ErrorCode.STRIPE_ERROR, "Failed to create subscription: " + ex.getMessage());
        }
    }

    public void cancelSubscription(String subscriptionId) {
        requireConfigured();
        try {
            Subscription subscription = Subscription.retrieve(subscriptionId);
            subscription.cancel((SubscriptionCancelParams) null);
        } catch (StripeException ex) {
            log.error("Stripe cancelSubscription failed: {}", ex.getMessage());
            throw new FitVisionException(ErrorCode.STRIPE_ERROR, "Failed to cancel subscription: " + ex.getMessage());
        }
    }

    public Subscription getSubscription(String subscriptionId) {
        requireConfigured();
        try {
            return Subscription.retrieve(subscriptionId);
        } catch (StripeException ex) {
            log.error("Stripe getSubscription failed: {}", ex.getMessage());
            throw new FitVisionException(ErrorCode.STRIPE_ERROR, "Failed to retrieve subscription: " + ex.getMessage());
        }
    }

    public String createCheckoutSession(String customerId, String priceId, String successUrl, String cancelUrl) {
        requireConfigured();
        try {
            com.stripe.param.checkout.SessionCreateParams params =
                    com.stripe.param.checkout.SessionCreateParams.builder()
                            .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
                            .setCustomer(customerId)
                            .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                                    .setPrice(priceId)
                                    .setQuantity(1L)
                                    .build())
                            .setSuccessUrl(successUrl)
                            .setCancelUrl(cancelUrl)
                            .build();
            com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(params);
            return session.getUrl();
        } catch (StripeException ex) {
            log.error("Stripe createCheckoutSession failed: {}", ex.getMessage());
            throw new FitVisionException(ErrorCode.STRIPE_ERROR, "Failed to create checkout session: " + ex.getMessage());
        }
    }

    public String createBillingPortalSession(String customerId, String returnUrl) {
        requireConfigured();
        try {
            com.stripe.param.billingportal.SessionCreateParams params =
                    com.stripe.param.billingportal.SessionCreateParams.builder()
                            .setCustomer(customerId)
                            .setReturnUrl(returnUrl)
                            .build();
            com.stripe.model.billingportal.Session session =
                    com.stripe.model.billingportal.Session.create(params);
            return session.getUrl();
        } catch (StripeException ex) {
            log.error("Stripe createBillingPortalSession failed: {}", ex.getMessage());
            throw new FitVisionException(ErrorCode.STRIPE_ERROR, "Failed to create billing portal session: " + ex.getMessage());
        }
    }

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new FitVisionException(ErrorCode.STRIPE_ERROR, "Stripe is not configured on this server.");
        }
    }
}
