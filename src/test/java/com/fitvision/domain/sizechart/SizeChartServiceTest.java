package com.fitvision.domain.sizechart;

import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.SizeChartRepository;
import com.fitvision.infrastructure.persistence.SizeEntryRepository;
import com.fitvision.shared.exception.ProductNotFoundException;
import com.fitvision.shared.exception.SizeChartParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SizeChartService}.
 *
 * <p>All dependencies are mocked with Mockito. No Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
class SizeChartServiceTest {

    @Mock
    private SizeChartRepository sizeChartRepository;

    @Mock
    private SizeEntryRepository sizeEntryRepository;

    @Mock
    private ProductRepository productRepository;

    private SizeChartService service;

    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SizeChartService(sizeChartRepository, sizeEntryRepository, productRepository);
    }

    // -----------------------------------------------------------------------
    // 1. uploadFromFile — happy path
    // -----------------------------------------------------------------------

    @Test
    void uploadFromFile_happyPath_deactivatesOldAndSavesNew() {
        // Arrange
        SizeEntryData entry = new SizeEntryData("M", 90.0, 96.0, 70.0, 76.0, 93.0, 99.0, 170.0, 180.0);
        ParseResult parseResult = ParseResult.success(List.of(entry), Collections.emptyList(), 0);

        com.fitvision.domain.product.Product product = new com.fitvision.domain.product.Product();
        product.setId(PRODUCT_ID);
        product.setTenantId(TENANT_ID);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.of(product));

        SizeChart savedChart = new SizeChart();
        savedChart.setId(UUID.randomUUID());
        savedChart.setProductId(PRODUCT_ID);
        savedChart.setVersion(1);
        savedChart.setActive(true);
        savedChart.setCreatedAt(LocalDateTime.now());
        when(sizeChartRepository.findAllByProductIdOrderByVersionDesc(PRODUCT_ID))
                .thenReturn(Collections.emptyList());
        when(sizeChartRepository.save(any(SizeChart.class))).thenReturn(savedChart);
        when(sizeEntryRepository.saveAll(any())).thenReturn(Collections.emptyList());

        // Act
        SizeChartUploadResult result = service.uploadFromFile(TENANT_ID, PRODUCT_ID, parseResult, "csv");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getEntriesSaved()).isEqualTo(1);
        verify(sizeChartRepository).deactivateAllByProductId(PRODUCT_ID);
        verify(sizeChartRepository).save(any(SizeChart.class));
        verify(sizeEntryRepository).saveAll(any());
    }

    // -----------------------------------------------------------------------
    // 2. uploadFromFile — ParseResult.failure → SizeChartParseException
    // -----------------------------------------------------------------------

    @Test
    void uploadFromFile_parseResultFailure_throwsSizeChartParseException() {
        ParseResult failResult = ParseResult.failure("Could not parse file");

        assertThatThrownBy(() -> service.uploadFromFile(TENANT_ID, PRODUCT_ID, failResult, "csv"))
                .isInstanceOf(SizeChartParseException.class)
                .hasMessageContaining("Could not parse file");

        verify(productRepository, never()).findByIdAndTenantId(any(), any());
        verify(sizeChartRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // 3. uploadFromFile — product not found → ProductNotFoundException
    // -----------------------------------------------------------------------

    @Test
    void uploadFromFile_productNotFound_throwsProductNotFoundException() {
        SizeEntryData entry = new SizeEntryData("M", 90.0, 96.0, 70.0, 76.0, 93.0, 99.0, 170.0, 180.0);
        ParseResult parseResult = ParseResult.success(List.of(entry), Collections.emptyList(), 0);

        when(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadFromFile(TENANT_ID, PRODUCT_ID, parseResult, "csv"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(sizeChartRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // 4. uploadManual — empty entries → SizeChartParseException
    // -----------------------------------------------------------------------

    @Test
    void uploadManual_emptyEntries_throwsSizeChartParseException() {
        assertThatThrownBy(() -> service.uploadManual(TENANT_ID, PRODUCT_ID, Collections.emptyList()))
                .isInstanceOf(SizeChartParseException.class)
                .hasMessageContaining("at least one size entry");

        verify(productRepository, never()).findByIdAndTenantId(any(), any());
    }

    // -----------------------------------------------------------------------
    // 5. getActiveSizeChart — no active chart → Optional.empty
    // -----------------------------------------------------------------------

    @Test
    void getActiveSizeChart_noActiveChart_returnsEmpty() {
        when(sizeChartRepository.findActiveByProductIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        Optional<SizeChart> result = service.getActiveSizeChart(TENANT_ID, PRODUCT_ID);

        assertThat(result).isEmpty();
    }
}
