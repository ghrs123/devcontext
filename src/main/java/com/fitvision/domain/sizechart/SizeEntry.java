package com.fitvision.domain.sizechart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "size_entries")
public class SizeEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "size_chart_id", nullable = false)
    private UUID sizeChartId;

    @Column(name = "size_label", nullable = false, length = 20)
    private String sizeLabel;

    @Column(name = "chest_min", precision = 5, scale = 1)
    private BigDecimal chestMin;

    @Column(name = "chest_max", precision = 5, scale = 1)
    private BigDecimal chestMax;

    @Column(name = "waist_min", precision = 5, scale = 1)
    private BigDecimal waistMin;

    @Column(name = "waist_max", precision = 5, scale = 1)
    private BigDecimal waistMax;

    @Column(name = "hip_min", precision = 5, scale = 1)
    private BigDecimal hipMin;

    @Column(name = "hip_max", precision = 5, scale = 1)
    private BigDecimal hipMax;

    @Column(name = "height_min", precision = 5, scale = 1)
    private BigDecimal heightMin;

    @Column(name = "height_max", precision = 5, scale = 1)
    private BigDecimal heightMax;

    @PrePersist
    @PreUpdate
    void normalizeSizeLabel() {
        if (sizeLabel != null) {
            sizeLabel = sizeLabel.toUpperCase();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSizeChartId() { return sizeChartId; }
    public void setSizeChartId(UUID sizeChartId) { this.sizeChartId = sizeChartId; }

    public String getSizeLabel() { return sizeLabel; }
    public void setSizeLabel(String sizeLabel) { this.sizeLabel = sizeLabel; }

    public BigDecimal getChestMin() { return chestMin; }
    public void setChestMin(BigDecimal chestMin) { this.chestMin = chestMin; }

    public BigDecimal getChestMax() { return chestMax; }
    public void setChestMax(BigDecimal chestMax) { this.chestMax = chestMax; }

    public BigDecimal getWaistMin() { return waistMin; }
    public void setWaistMin(BigDecimal waistMin) { this.waistMin = waistMin; }

    public BigDecimal getWaistMax() { return waistMax; }
    public void setWaistMax(BigDecimal waistMax) { this.waistMax = waistMax; }

    public BigDecimal getHipMin() { return hipMin; }
    public void setHipMin(BigDecimal hipMin) { this.hipMin = hipMin; }

    public BigDecimal getHipMax() { return hipMax; }
    public void setHipMax(BigDecimal hipMax) { this.hipMax = hipMax; }

    public BigDecimal getHeightMin() { return heightMin; }
    public void setHeightMin(BigDecimal heightMin) { this.heightMin = heightMin; }

    public BigDecimal getHeightMax() { return heightMax; }
    public void setHeightMax(BigDecimal heightMax) { this.heightMax = heightMax; }
}
