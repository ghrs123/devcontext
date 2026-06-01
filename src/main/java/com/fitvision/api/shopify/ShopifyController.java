package com.fitvision.api.shopify;

import com.fitvision.integration.shopify.ShopifyService;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shopify")
@Tag(name = "Shopify", description = "Shopify App integration endpoints")
public class ShopifyController {

    private static final Logger log = LoggerFactory.getLogger(ShopifyController.class);

    private final ShopifyService shopifyService;
    private final String shopifySharedSecret;

    public ShopifyController(
            ShopifyService shopifyService,
            @Value("${fitvision.shopify.shared-secret}") String shopifySharedSecret) {
        this.shopifyService = shopifyService;
        this.shopifySharedSecret = shopifySharedSecret;
    }

    /**
     * Called by the Shopify App server after OAuth to link a Shopify store to FitVision.
     * Protected by a shared secret header instead of JWT — this endpoint is server-to-server.
     */
    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<ShopifyConnectResponse>> connect(
            @RequestHeader("X-FitVision-Shopify-Secret") String secret,
            @Valid @RequestBody ShopifyConnectRequest request) {

        if (!shopifySharedSecret.equals(secret)) {
            log.warn("Rejected /api/shopify/connect — invalid shared secret");
            throw new FitVisionException(ErrorCode.UNAUTHORIZED, "Invalid Shopify shared secret.");
        }

        ShopifyService.ShopifyConnectResult result =
                shopifyService.connectStore(request.getShop(), request.getAccessToken(), request.getShopName());

        ShopifyConnectResponse response = new ShopifyConnectResponse(
                result.jwt(), result.apiKeyPublic(), result.storeId());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Returns whether a Shopify shop domain is connected to a FitVision account.
     * Used by the embedded Shopify App UI to show connection status.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ShopifyStatusResponse>> status(
            @RequestParam String shop) {

        ShopifyService.ShopifyStatusResult result = shopifyService.getStatus(shop);
        ShopifyStatusResponse response = new ShopifyStatusResponse(
                result.connected(), result.storeId(), result.apiKeyPublic());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
