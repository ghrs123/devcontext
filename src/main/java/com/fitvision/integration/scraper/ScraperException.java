package com.fitvision.integration.scraper;

public class ScraperException extends RuntimeException {

    private final String sourceUrl;

    public ScraperException(String message, String sourceUrl) {
        super(message);
        this.sourceUrl = sourceUrl;
    }

    public ScraperException(String message, Throwable cause, String sourceUrl) {
        super(message, cause);
        this.sourceUrl = sourceUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }
}
