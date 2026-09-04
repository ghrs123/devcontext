package com.fitvision.integration.scraper;

import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.product.Product;
import com.fitvision.domain.scraping.ScrapeJob;
import com.fitvision.domain.scraping.ScrapeJobStatus;
import com.fitvision.domain.sizechart.SizeChartService;
import com.fitvision.domain.sizechart.SizeChartUploadResult;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.ScrapeJobRepository;
import com.fitvision.infrastructure.persistence.SizeChartRepository;
import com.fitvision.infrastructure.persistence.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScraperServiceTest {

    @Mock private BrandRepository brandRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SizeChartService sizeChartService;
    @Mock private SizeChartRepository sizeChartRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private ScrapeJobRepository scrapeJobRepository;
    @Mock private BrandScraperRegistry scraperRegistry;
    @Mock private BrandScraper scraper;

    private ScraperService service;

    private final UUID brandId = UUID.randomUUID();
    private final UUID adminStoreId = UUID.randomUUID();
    private final Brand zara = Brand.builder().id(brandId).slug("zara").name("Zara").build();

    @BeforeEach
    void setUp() {
        service = new ScraperService(brandRepository, productRepository, sizeChartService,
                sizeChartRepository, storeRepository, scrapeJobRepository, scraperRegistry);
        when(brandRepository.findGlobalById(brandId)).thenReturn(Optional.of(zara));
        when(scrapeJobRepository.save(any(ScrapeJob.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private BrandScraper.ScrapePayload payload(List<SizeEntryData> tops) {
        return new BrandScraper.ScrapePayload(Map.of("tops", tops), "https://www.zara.com", 1, List.of());
    }

    private List<SizeEntryData> realisticChart() {
        return List.of(
                new SizeEntryData("S", 88.0, 96.0, 74.0, 82.0, null, null, null, null),
                new SizeEntryData("M", 96.0, 104.0, 82.0, 90.0, null, null, null, null),
                new SizeEntryData("L", 104.0, 112.0, 90.0, 98.0, null, null, null, null));
    }

    @Test
    void given_scraperReturnsRealChart_when_triggerNow_then_jobCompletedAndChartUploaded() throws Exception {
        when(scraperRegistry.findBySlug("zara")).thenReturn(Optional.of(scraper));
        when(scraper.scrape(zara)).thenReturn(payload(realisticChart()));
        Product template = new Product();
        template.setId(UUID.randomUUID());
        template.setBrandId(brandId);
        when(productRepository.findAnyByExternalProductIdAndTenantId(any(), eq(adminStoreId)))
                .thenReturn(Optional.of(template));
        when(sizeChartService.uploadManual(eq(adminStoreId), eq(template.getId()), anyList()))
                .thenReturn(SizeChartUploadResult.of(UUID.randomUUID(), 1, 3, List.of()));
        when(sizeChartService.getActiveSizeChart(adminStoreId, template.getId())).thenReturn(Optional.empty());

        ScrapeJob job = service.triggerNow(brandId, adminStoreId);

        assertThat(job.getStatus()).isEqualTo(ScrapeJobStatus.COMPLETED);
        assertThat(job.getEntriesFound()).isEqualTo(3);
        verify(sizeChartService).uploadManual(eq(adminStoreId), eq(template.getId()), anyList());
        verify(brandRepository).save(zara);
        assertThat(zara.getLastScrapedAt()).isNotNull();
    }

    @Test
    void given_scraperThrows_when_triggerNow_then_jobFailedAndChartNotTouched() throws Exception {
        when(scraperRegistry.findBySlug("zara")).thenReturn(Optional.of(scraper));
        when(scraper.scrape(zara)).thenThrow(new IllegalStateException("robots.txt disallows scraping"));

        ScrapeJob job = service.triggerNow(brandId, adminStoreId);

        assertThat(job.getStatus()).isEqualTo(ScrapeJobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("robots.txt");
        verify(sizeChartService, never()).uploadManual(any(), any(), anyList());
    }

    @Test
    void given_scraperReturnsNoEntries_when_triggerNow_then_jobFailedAndChartNotTouched() throws Exception {
        when(scraperRegistry.findBySlug("zara")).thenReturn(Optional.of(scraper));
        when(scraper.scrape(zara)).thenReturn(payload(List.of()));

        ScrapeJob job = service.triggerNow(brandId, adminStoreId);

        assertThat(job.getStatus()).isEqualTo(ScrapeJobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("No entries scraped");
        verify(sizeChartService, never()).uploadManual(any(), any(), anyList());
    }

    @Test
    void given_scraperReturnsThinJunk_when_triggerNow_then_jobFailedAndChartNotTouched() throws Exception {
        when(scraperRegistry.findBySlug("zara")).thenReturn(Optional.of(scraper));
        // One label, no measurement bounds — what the current Zara scraper actually produces
        // when it stumbles onto an unrelated <table> after the markup changes.
        when(scraper.scrape(zara)).thenReturn(payload(List.of(
                new SizeEntryData("VIEW ALL", null, null, null, null, null, null, null, null),
                new SizeEntryData("VIEW ALL", null, null, null, null, null, null, null, null))));

        ScrapeJob job = service.triggerNow(brandId, adminStoreId);

        assertThat(job.getStatus()).isEqualTo(ScrapeJobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("refusing to replace the active chart");
        verify(sizeChartService, never()).uploadManual(any(), any(), anyList());
    }

    @Test
    void given_noScraperRegisteredForSlug_when_triggerNow_then_jobFailed() {
        when(scraperRegistry.findBySlug("zara")).thenReturn(Optional.empty());

        ScrapeJob job = service.triggerNow(brandId, adminStoreId);

        assertThat(job.getStatus()).isEqualTo(ScrapeJobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("No scraper registered");
    }

    @Test
    void given_pendingJob_when_runPendingJob_then_reusesTheSameJobRow() throws Exception {
        UUID jobId = UUID.randomUUID();
        ScrapeJob pending = new ScrapeJob();
        pending.setId(jobId);
        pending.setBrandId(brandId);
        pending.setStatus(ScrapeJobStatus.PENDING);
        when(scrapeJobRepository.findById(jobId)).thenReturn(Optional.of(pending));
        when(scraperRegistry.findBySlug("zara")).thenReturn(Optional.of(scraper));
        when(scraper.scrape(zara)).thenThrow(new IllegalStateException("robots.txt disallows scraping"));

        ScrapeJob result = service.runPendingJob(jobId, adminStoreId);

        // Same row, not a second one.
        assertThat(result.getId()).isEqualTo(jobId);
        assertThat(result.getStatus()).isEqualTo(ScrapeJobStatus.FAILED);
        // No new PENDING job was ever created — save() only ever persists `pending`.
        verify(scrapeJobRepository, never()).save(argThat(j -> j != pending));
    }
}
