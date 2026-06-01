package com.fitvision.integration.scraper;

import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ZaraScraper implements BrandScraper {

    private static final Logger log = LoggerFactory.getLogger(ZaraScraper.class);

    private static final String ZARA_SLUG = "zara";
    private static final String ZARA_HOME = "https://www.zara.com";

    @Override
    public String supportedSlug() {
        return ZARA_SLUG;
    }

    @Override
    public ScrapePayload scrape(Brand brand) throws Exception {
        List<String> warnings = new ArrayList<>();
        String sourceUrl = ZARA_HOME;
        int pagesScraped = 0;

        if (!isRobotsAllowed(sourceUrl)) {
            throw new IllegalStateException("robots.txt disallows scraping for " + sourceUrl);
        }

        List<SizeEntryData> topsEntries = new ArrayList<>();

        // Start with homepage bootstrap, then try a generic table extraction.
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate(sourceUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            pagesScraped++;

            // Attempt a lightweight extraction for men's tops tables if present.
            // Zara frequently changes markup; we keep this defensive and non-fatal.
            var rows = page.querySelectorAll("table tr");
            for (var row : rows) {
                var cells = row.querySelectorAll("th,td");
                if (cells.size() < 3) {
                    continue;
                }

                String size = safeText(cells.get(0));
                Double chestMin = parseNumeric(safeText(cells.get(1)));
                Double chestMax = parseNumeric(safeText(cells.get(2)));

                if (size == null || size.isBlank()) {
                    continue;
                }
                topsEntries.add(new SizeEntryData(size.toUpperCase(), chestMin, chestMax, null, null, null, null, null, null));
            }

            browser.close();
        } catch (Exception ex) {
            warnings.add("Zara parsing step failed: " + ex.getMessage());
            log.warn("Zara scraper parsing step failed", ex);
        }

        if (topsEntries.isEmpty()) {
            warnings.add("No size rows found on Zara target page.");
        }

        Map<String, List<SizeEntryData>> byCategory = new HashMap<>();
        byCategory.put("tops", topsEntries);

        return new ScrapePayload(byCategory, sourceUrl, pagesScraped, warnings);
    }

    private boolean isRobotsAllowed(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
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
            // Fail-safe: if robots cannot be read, skip scraping for safety.
            log.warn("Could not read robots.txt for Zara: {}", ex.getMessage());
            return false;
        }
    }

    private String safeText(com.microsoft.playwright.ElementHandle handle) {
        try {
            return handle.innerText().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private Double parseNumeric(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9.,-]", "").replace(',', '.');
        if (digits.isBlank()) {
            return null;
        }
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

}
