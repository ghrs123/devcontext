ALTER TABLE stores ADD COLUMN shopify_shop VARCHAR(255) UNIQUE;
ALTER TABLE stores ADD COLUMN shopify_access_token_encrypted TEXT;
CREATE INDEX idx_stores_shopify_shop ON stores(shopify_shop);
