package com.fitvision.domain.sizechart;

import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.SizeChartRepository;
import com.fitvision.infrastructure.persistence.SizeEntryRepository;
import com.fitvision.shared.exception.ProductNotFoundException;
import com.fitvision.shared.exception.SizeChartParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages persistence and versioning of size charts.
 *
 * <p>Versioning rule: each upload creates a new {@link SizeChart} version.
 * The new version is set to {@code active=true} and all prior versions for the same
 * product are set to {@code active=false}. Only one version can be active per product
 * at a time.
 */
@Service
public class SizeChartService {

    private static final Logger log = LoggerFactory.getLogger(SizeChartService.class);

    private final SizeChartRepository sizeChartRepository;
    private final SizeEntryRepository sizeEntryRepository;
    private final ProductRepository productRepository;

    public SizeChartService(SizeChartRepository sizeChartRepository,
                            SizeEntryRepository sizeEntryRepository,
                            ProductRepository productRepository) {
        this.sizeChartRepository = sizeChartRepository;
        this.sizeEntryRepository = sizeEntryRepository;
        this.productRepository = productRepository;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Persists a size chart from a file parse result.
     *
     * @param tenantId    the authenticated tenant
     * @param productId   the product to attach the chart to
     * @param parseResult the result of parsing the uploaded file
     * @param source      a label for the upload origin (e.g. "csv", "xlsx")
     * @return upload result with version number, entry count, and any parser warnings
     * @throws SizeChartParseException  if the parse result is a failure or has no entries
     * @throws ProductNotFoundException if the product does not belong to this tenant
     */
    @Transactional
    public SizeChartUploadResult uploadFromFile(UUID tenantId, UUID productId,
                                                ParseResult parseResult, String source) {
        if (!parseResult.isSuccess()) {
            String reason = parseResult.getWarnings().isEmpty()
                    ? "Parse failed with no details."
                    : parseResult.getWarnings().get(0);
            throw new SizeChartParseException("File parsing failed: " + reason);
        }
        if (parseResult.getEntries().isEmpty()) {
            throw new SizeChartParseException(
                    "The uploaded file contains no valid size entries after parsing.");
        }
        return persist(tenantId, productId, parseResult.getEntries(), source, parseResult.getWarnings());
    }

    /**
     * Persists a size chart from a manually supplied list of entries.
     *
     * @throws SizeChartParseException  if entries is null or empty
     * @throws ProductNotFoundException if the product does not belong to this tenant
     */
    @Transactional
    public SizeChartUploadResult uploadManual(UUID tenantId, UUID productId,
                                              List<SizeEntryData> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new SizeChartParseException("Manual entry requires at least one size entry.");
        }
        return persist(tenantId, productId, entries, "manual", Collections.emptyList());
    }

    /**
     * Returns the currently active {@link SizeChart} for a product, if one exists.
     */
    public Optional<SizeChart> getActiveSizeChart(UUID tenantId, UUID productId) {
        return sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId);
    }

    /**
     * Returns the entries of the active size chart for a product as {@link SizeEntryData} records.
     * Returns an empty list (not an error) when no active chart exists.
     */
    public List<SizeEntryData> getActiveSizeChartEntries(UUID tenantId, UUID productId) {
        return sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId)
                .map(chart -> {
                    List<SizeEntry> entities = sizeEntryRepository.findAllBySizeChartId(chart.getId());
                    return entities.stream()
                            .map(this::toSizeEntryData)
                            .toList();
                })
                .orElse(Collections.emptyList());
    }

    /**
     * Validates that the product belongs to the tenant, then soft-deactivates the active size chart.
     * If no active chart exists, this is a no-op (idempotent).
     *
     * @throws ProductNotFoundException if the product does not belong to this tenant
     */
    @Transactional
    public void deactivateActiveSizeChart(UUID tenantId, UUID productId) {
        productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product " + productId + " not found for tenant " + tenantId));

        sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId)
                .ifPresent(chart -> {
                    chart.setActive(false);
                    sizeChartRepository.save(chart);
                    log.info("SizeChart deactivated: tenantId={} productId={} chartId={}",
                            tenantId, productId, chart.getId());
                });
    }

    /**
     * Validates that the product belongs to the tenant, then returns the active chart.
     *
     * @throws ProductNotFoundException if the product does not belong to this tenant
     */
    public Optional<SizeChart> getActiveSizeChartForTenant(UUID tenantId, UUID productId) {
        productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product " + productId + " not found for tenant " + tenantId));
        return sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId);
    }

    // -----------------------------------------------------------------------
    // Internal — transactional persistence
    // -----------------------------------------------------------------------

    /**
     * Steps 3–7 of the upload flow, executed atomically.
     */
    @Transactional
    protected SizeChartUploadResult persist(UUID tenantId, UUID productId,
                                            List<SizeEntryData> entries, String source,
                                            List<String> warnings) {
        // Step 3: verify product belongs to this tenant
        productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product " + productId + " not found for tenant " + tenantId));

        // Step 4: deactivate all existing versions
        sizeChartRepository.deactivateAllByProductId(productId);

        // Step 5: compute next version number
        int nextVersion = sizeChartRepository
                .findAllByProductIdOrderByVersionDesc(productId)
                .stream()
                .findFirst()
                .map(sc -> sc.getVersion() + 1)
                .orElse(1);

        // Step 6: create new SizeChart
        SizeChart chart = new SizeChart();
        chart.setId(UUID.randomUUID());
        chart.setProductId(productId);
        chart.setVersion(nextVersion);
        chart.setSource(source);
        chart.setActive(true);
        chart.setCreatedAt(LocalDateTime.now());
        sizeChartRepository.save(chart);

        // Step 7: persist entries
        List<SizeEntry> entitiesList = new ArrayList<>(entries.size());
        for (SizeEntryData data : entries) {
            SizeEntry entry = toSizeEntry(data, chart.getId());
            entitiesList.add(entry);
        }
        sizeEntryRepository.saveAll(entitiesList);

        log.info("SizeChart saved: tenantId={} productId={} version={} entriesSaved={}",
                tenantId, productId, nextVersion, entitiesList.size());

        return SizeChartUploadResult.of(chart.getId(), nextVersion, entitiesList.size(),
                new ArrayList<>(warnings));
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private SizeEntry toSizeEntry(SizeEntryData data, UUID sizeChartId) {
        SizeEntry e = new SizeEntry();
        e.setId(UUID.randomUUID());
        e.setSizeChartId(sizeChartId);
        e.setSizeLabel(data.sizeLabel());
        e.setChestMin(toBigDecimal(data.chestMin()));
        e.setChestMax(toBigDecimal(data.chestMax()));
        e.setWaistMin(toBigDecimal(data.waistMin()));
        e.setWaistMax(toBigDecimal(data.waistMax()));
        e.setHipMin(toBigDecimal(data.hipMin()));
        e.setHipMax(toBigDecimal(data.hipMax()));
        e.setHeightMin(toBigDecimal(data.heightMin()));
        e.setHeightMax(toBigDecimal(data.heightMax()));
        return e;
    }

    private SizeEntryData toSizeEntryData(SizeEntry e) {
        return new SizeEntryData(
                e.getSizeLabel(),
                toDouble(e.getChestMin()),
                toDouble(e.getChestMax()),
                toDouble(e.getWaistMin()),
                toDouble(e.getWaistMax()),
                toDouble(e.getHipMin()),
                toDouble(e.getHipMax()),
                toDouble(e.getHeightMin()),
                toDouble(e.getHeightMax())
        );
    }

    private static BigDecimal toBigDecimal(Double value) {
        if (value == null) return null;
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
