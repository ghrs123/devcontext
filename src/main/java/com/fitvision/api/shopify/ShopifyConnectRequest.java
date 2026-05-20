package com.fitvision.api.shopify;

import jakarta.validation.constraints.NotBlank;

public class ShopifyConnectRequest {

    @NotBlank
    private String shop;

    @NotBlank
    private String accessToken;

    private String shopName;

    public String getShop() { return shop; }
    public void setShop(String shop) { this.shop = shop; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
}
