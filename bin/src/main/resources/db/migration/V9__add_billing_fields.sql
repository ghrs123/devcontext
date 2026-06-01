-- Phase 10: Billing & Subscriptions
-- Note: subscription_status already exists from V1. Only new columns are added here.

ALTER TABLE stores
    ADD COLUMN stripe_customer_id          VARCHAR(255) UNIQUE,
    ADD COLUMN stripe_subscription_id      VARCHAR(255) UNIQUE,
    ADD COLUMN stripe_price_id             VARCHAR(255),
    ADD COLUMN subscription_current_period_end       TIMESTAMP,
    ADD COLUMN recommendations_count_current_month   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN recommendations_count_reset_at        TIMESTAMP;

CREATE INDEX idx_stores_stripe_customer      ON stores(stripe_customer_id);
CREATE INDEX idx_stores_stripe_subscription  ON stores(stripe_subscription_id);
