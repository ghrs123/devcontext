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

// Pull&Bear is an Inditex brand — similar HTML structure to Zara.
@Component
public class PullAndBearScraper extends AbstractBrandScraper {

    private static final String SLUG = "pull-and-bear";
    private static final String BASE_URL = "https://www.pullandbear.com";
    private static final String SIZE_GUIDE_URL = BASE_URL + "/pt/guia-de-tamanhos.html";

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

                    List<SizeEntryData> tops = extractSizeRows(page, warnings, SIZE_GUIDE_URL);
                    if (!tops.isEmpty()) {
                        byCategory.put("tops", tops);
                    }

                    List<SizeEntryData> bottoms = extractBottomRows(page, warnings, SIZE_GUIDE_URL);
                    if (!bottoms.isEmpty()) {
                        byCategory.put("bottoms", bottoms);
                    }
                } catch (Exception ex) {
                    warnings.add("Pull&Bear size guide page failed: " + ex.getMessage());
                    log.warn("Pull&Bear size guide scrape failed", ex);
                }

            } finally {
                browser.close();
            }
        } catch (Exception ex) {
            warnings.add("Pull&Bear scraper browser error: " + ex.getMessage());
            log.warn("Pull&Bear scraper browser error", ex);
        }

        if (byCategory.isEmpty()) {
            warnings.add("No size rows found on Pull&Bear page.");
        }

        return new ScrapePayload(byCategory, SIZE_GUIDE_URL, pagesScraped, warnings);
    }

    // Extracts the first size table found — typically the tops/chest-based table.
    private List<SizeEntryData> extractSizeRows(Page page, List<String> warnings, String pageUrl) {
        List<SizeEntryData> entries = new ArrayList<>();
        try {
            var tables = page.querySelectorAll("table");
            if (tables.isEmpty()) return entries;

            var rows = tables.get(0).querySelectorAll("tr");
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
            warnings.add("Tops table extraction failed on " + pageUrl + ": " + ex.getMessage());
        }
        return entries;
    }

    // Extracts the second table if present — typically waist-based (bottoms).
    private List<SizeEntryData> extractBottomRows(Page page, List<String> warnings, String pageUrl) {
        List<SizeEntryData> entries = new ArrayList<>();
        try {
            var tables = page.querySelectorAll("table");
            if (tables.size() < 2) return entries;

            var rows = tables.get(1).querySelectorAll("tr");
            for (var row : rows) {
                try {
                    var cells = row.querySelectorAll("th,td");
                    if (cells.size() < 2) continue;

                    String size = safeText(cells.get(0));
                    if (size == null || size.isBlank()) continue;

                    Double garmentWaist = cells.size() > 1 ? parseNumeric(safeText(cells.get(1))) : null;
                    Double garmentHip   = cells.size() > 2 ? parseNumeric(safeText(cells.get(2))) : null;

                    entries.add(new SizeEntryData(
                            size.toUpperCase(),
                            null,
                            null,
                            applyOffset(garmentWaist, WAIST_MIN_OFFSET),
                            applyOffset(garmentWaist, WAIST_MAX_OFFSET),
                            applyOffset(garmentHip,   HIP_MIN_OFFSET),
                            applyOffset(garmentHip,   HIP_MAX_OFFSET),
                            null,
                            null
                    ));
                } catch (Exception rowEx) {
                    warnings.add("Skipped bottoms row on " + pageUrl + ": " + rowEx.getMessage());
                }
            }
        } catch (Exception ex) {
            warnings.add("Bottoms table extraction failed on " + pageUrl + ": " + ex.getMessage());
        }
        return entries;
    }
}
