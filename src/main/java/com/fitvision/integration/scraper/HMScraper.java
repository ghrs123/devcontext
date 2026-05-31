package com.fitvision.integration.scraper;

import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HMScraper extends AbstractBrandScraper {

    private static final String SLUG = "hm";
    private static final String BASE_URL = "https://www2.hm.com";
    private static final String MENS_URL = BASE_URL + "/pt_pt/customerservice/sizeguide/mens.html";
    private static final String LADIES_URL = BASE_URL + "/pt_pt/customerservice/sizeguide/ladies.html";

    @Override
    public String supportedSlug() {
        return SLUG;
    }

    @Override
    public ScrapePayload scrape(Brand brand) {
        List<String> warnings = new ArrayList<>();
        int pagesScraped = 0;

        if (!isRobotsAllowed(BASE_URL)) {
            throw new IllegalStateException("robots.txt disallows scraping for " + BASE_URL);
        }

        Map<String, List<SizeEntryData>> byCategory = new HashMap<>();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchBrowser(playwright);
            try {
                Page page = browser.newPage();
                page.setExtraHTTPHeaders(Map.of("User-Agent", USER_AGENT));

                List<SizeEntryData> mensTops = new ArrayList<>();
                try {
                    page.navigate(MENS_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                    pagesScraped++;
                    mensTops = extractSizeRows(page, warnings, MENS_URL);
                } catch (Exception ex) {
                    warnings.add("H&M men's page failed: " + ex.getMessage());
                    log.warn("H&M men's page scrape failed", ex);
                }
                if (!mensTops.isEmpty()) {
                    byCategory.put("tops", mensTops);
                }

                List<SizeEntryData> ladiesTops = new ArrayList<>();
                try {
                    page.navigate(LADIES_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                    pagesScraped++;
                    ladiesTops = extractSizeRows(page, warnings, LADIES_URL);
                } catch (Exception ex) {
                    warnings.add("H&M ladies page failed: " + ex.getMessage());
                    log.warn("H&M ladies page scrape failed", ex);
                }
                if (!ladiesTops.isEmpty() && !byCategory.containsKey("tops")) {
                    byCategory.put("tops", ladiesTops);
                }

            } finally {
                browser.close();
            }
        } catch (Exception ex) {
            warnings.add("H&M scraper browser error: " + ex.getMessage());
            log.warn("H&M scraper browser error", ex);
        }

        if (byCategory.isEmpty()) {
            warnings.add("No size rows found on H&M pages.");
        }

        return new ScrapePayload(byCategory, MENS_URL, pagesScraped, warnings);
    }

    private List<SizeEntryData> extractSizeRows(Page page, List<String> warnings, String pageUrl) {
        List<SizeEntryData> entries = new ArrayList<>();
        try {
            var rows = page.querySelectorAll("table tr");
            for (var row : rows) {
                try {
                    var cells = row.querySelectorAll("th,td");
                    if (cells.size() < 2) continue;

                    String size = safeText(cells.get(0));
                    if (size == null || size.isBlank()) continue;

                    Double garmentChest = cells.size() > 1 ? parseNumeric(safeText(cells.get(1))) : null;
                    Double garmentWaist = cells.size() > 2 ? parseNumeric(safeText(cells.get(2))) : null;
                    Double garmentHip   = cells.size() > 3 ? parseNumeric(safeText(cells.get(3))) : null;

                    entries.add(new SizeEntryData(
                            size.toUpperCase(),
                            applyOffset(garmentChest, CHEST_MIN_OFFSET),
                            applyOffset(garmentChest, CHEST_MAX_OFFSET),
                            applyOffset(garmentWaist, WAIST_MIN_OFFSET),
                            applyOffset(garmentWaist, WAIST_MAX_OFFSET),
                            applyOffset(garmentHip,   HIP_MIN_OFFSET),
                            applyOffset(garmentHip,   HIP_MAX_OFFSET),
                            null,
                            null
                    ));
                } catch (Exception rowEx) {
                    warnings.add("Skipped row on " + pageUrl + ": " + rowEx.getMessage());
                }
            }
        } catch (Exception ex) {
            warnings.add("Table extraction failed on " + pageUrl + ": " + ex.getMessage());
        }
        return entries;
    }
}
