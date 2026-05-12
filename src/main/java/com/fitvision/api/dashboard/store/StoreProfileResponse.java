package com.fitvision.api.dashboard.store;

import java.util.UUID;

public class StoreProfileResponse {

    private UUID id;
    private String name;
    private String email;
    private String plan;
    private String platform;
    private String apiKeyPublic;
    private String subscriptionStatus;

    public StoreProfileResponse(UUID id,
                                String name,
                                String email,
                                String plan,
                                String platform,
                                String apiKeyPublic,
                                String subscriptionStatus) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.plan = plan;
        this.platform = platform;
        this.apiKeyPublic = apiKeyPublic;
        this.subscriptionStatus = subscriptionStatus;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPlan() {
        return plan;
    }

    public String getPlatform() {
        return platform;
    }

    public String getApiKeyPublic() {
        return apiKeyPublic;
    }

    public String getSubscriptionStatus() {
        return subscriptionStatus;
    }
}
