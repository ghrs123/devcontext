package com.fitvision.api.shopify;

import java.util.UUID;

public class ShopifyConnectResponse {

    private final String jwt;
    private final String apiKeyPublic;
    private final UUID storeId;

    public ShopifyConnectResponse(String jwt, String apiKeyPublic, UUID storeId) {
        this.jwt = jwt;
        this.apiKeyPublic = apiKeyPublic;
        this.storeId = storeId;
    }

    public String getJwt() { return jwt; }
    public String getApiKeyPublic() { return apiKeyPublic; }
    public UUID getStoreId() { return storeId; }
}
