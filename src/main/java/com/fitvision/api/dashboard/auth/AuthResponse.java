package com.fitvision.api.dashboard.auth;

public class AuthResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String apiKeyPublic;

    public AuthResponse(String accessToken, String tokenType, long expiresIn, String apiKeyPublic) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.apiKeyPublic = apiKeyPublic;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public String getApiKeyPublic() {
        return apiKeyPublic;
    }
}
