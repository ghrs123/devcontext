package com.fitvision.api.shopify;

import java.util.UUID;

public class ShopifyStatusResponse {

    private final boolean connected;
    private final UUID storeId;
    private final String apiKeyPublic;

    public ShopifyStatusResponse(boolean connected, UUID storeId, String apiKeyPublic) {
        this.connected = connected;
        this.storeId = storeId;
        this.apiKeyPublic = apiKeyPublic;
    }

    public boolean isConnected() { return connected; }
    public UUID getStoreId() { return storeId; }
    public String getApiKeyPublic() { return apiKeyPublic; }
}
