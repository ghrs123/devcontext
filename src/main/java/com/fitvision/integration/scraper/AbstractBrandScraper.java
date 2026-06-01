package com.fitvision.integration.scraper;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

public abstract class AbstractBrandScraper implements BrandScraper {

    protected static final String USER_AGENT =
            "Mozilla/5.0 (compatible; FitVision-Scraper/1.0; +https://fitvision.io/bot)";

    protected static final double CHEST_MIN_OFFSET = -4.0;
    protected static final double CHEST_MAX_OFFSET = 2.0;
    protected static final double WAIST_MIN_OFFSET = -3.0;
    protected static final double WAIST_MAX_OFFSET = 2.0;
    protected static final double HIP_MIN_OFFSET = -4.0;
    protected static final double HIP_MAX_OFFSET = 2.0;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected boolean isRobotsAllowed(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            URL robotsUrl = new URL(uri.getScheme() + "://" + uri.getHost() + "/robots.txt");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(robotsUrl.openStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean inGlobalAgent = false;
                while ((line = reader.readLine()) != null) {
                    String normalized = line.trim().toLowerCase();
                    if (normalized.startsWith("user-agent:")) {
                        inGlobalAgent = normalized.equals("user-agent: *");
                        continue;
                    }
                    if (inGlobalAgent && normalized.startsWith("disallow:")) {
                        String value = normalized.substring("disallow:".length()).trim();
                        if ("/".equals(value)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (Exception ex) {
            log.warn("Could not read robots.txt for {}: {}", baseUrl, ex.getMessage());
            return false;
        }
    }

    protected Browser launchBrowser(Playwright playwright) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of("--no-sandbox", "--disable-setuid-sandbox"));
        String chromiumPath = System.getenv("CHROMIUM_PATH");
        if (chromiumPath != null && !chromiumPath.isBlank()) {
            options.setExecutablePath(Paths.get(chromiumPath));
        }
        return playwright.chromium().launch(options);
    }

    protected String safeText(ElementHandle handle) {
        try {
            return handle.innerText().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    protected Double parseNumeric(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9.,-]", "").replace(',', '.');
        if (digits.isBlank()) return null;
        if (digits.contains("-")) {
            String[] parts = digits.split("-");
            if (parts.length > 0 && !parts[0].isBlank()) {
                digits = parts[0];
            }
        }
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Returns garment + offset, or null if garment is null.
    protected Double applyOffset(Double garment, double offset) {
        return garment == null ? null : garment + offset;
    }
}
