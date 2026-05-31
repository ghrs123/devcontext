package com.fitvision.integration.scraper;

import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MangoScraper extends AbstractBrandScraper {

    private static final String SLUG = "mango";
    private static final String BASE_URL = "https://shop.mango.com";
    private static final String SIZE_GUIDE_URL = BASE_URL + "/pt/pt/guia-de-tamanhos";

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

                try {
                    page.navigate(SIZE_GUIDE_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                    pagesScraped++;

                    // Mango uses multiple tables — map each to a category by position or header text.
                    var tables = page.querySelectorAll("table");
                    String[] categoryOrder = {"tops", "bottoms", "dresses"};

                    for (int i = 0; i < tables.size() && i < categoryOrder.length; i++) {
                        String category = categoryOrder[i];
                        List<SizeEntryData> entries = extractTableRows(tables.get(i), warnings, SIZE_GUIDE_URL, category);
                        if (!entries.isEmpty()) {
                            byCategory.put(category, entries);
                        }
                    }
                } catch (Exception ex) {
                    warnings.add("Mango size guide page failed: " + ex.getMessage());
                    log.warn("Mango size guide scrape failed", ex);
                }

            } finally {
                browser.close();
            }
        } catch (Exception ex) {
            warnings.add("Mango scraper browser error: " + ex.getMessage());
            log.warn("Mango scraper browser error", ex);
        }

        if (byCategory.isEmpty()) {
            warnings.add("No size rows found on Mango page.");
        }

        return new ScrapePayload(byCategory, SIZE_GUIDE_URL, pagesScraped, warnings);
    }

    private List<SizeEntryData> extractTableRows(ElementHandle table, List<String> warnings,
                                                  String pageUrl, String category) {
        List<SizeEntryData> entries = new ArrayList<>();
        try {
            var rows = table.querySelectorAll("tr");
            for (var row : rows) {
                try {
                    var cells = row.querySelectorAll("th,td");
                    if (cells.size() < 2) continue;

                    String size = safeText(cells.get(0));
                    if (size == null || size.isBlank()) continue;

                    boolean isTopsOrDresses = "tops".equals(category) || "dresses".equals(category);

                    Double garmentChest = isTopsOrDresses && cells.size() > 1
                            ? parseNumeric(safeText(cells.get(1))) : null;
                    Double garmentWaist = cells.size() > 2
                            ? parseNumeric(safeText(cells.get(2))) : null;
                    Double garmentHip   = cells.size() > 3
                            ? parseNumeric(safeText(cells.get(3))) : null;

                    // For bottoms, column 1 is waist (no chest column).
                    if ("bottoms".equals(category)) {
                        garmentChest = null;
                        garmentWaist = cells.size() > 1 ? parseNumeric(safeText(cells.get(1))) : null;
                        garmentHip   = cells.size() > 2 ? parseNumeric(safeText(cells.get(2))) : null;
                    }

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
                    warnings.add("Skipped " + category + " row on " + pageUrl + ": " + rowEx.getMessage());
                }
            }
        } catch (Exception ex) {
            warnings.add("Table extraction failed for " + category + " on " + pageUrl + ": " + ex.getMessage());
        }
        return entries;
    }
}
