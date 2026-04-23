package com.fitvision.domain.sizechart;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Value object returned after a size chart upload (file or manual).
 */
public final class SizeChartUploadResult {

    private final UUID sizeChartId;
    private final int version;
    private final int entriesSaved;
    private final List<String> warnings;
    private final boolean success;

    private SizeChartUploadResult(UUID sizeChartId, int version, int entriesSaved,
                                  List<String> warnings, boolean success) {
        this.sizeChartId = sizeChartId;
        this.version = version;
        this.entriesSaved = entriesSaved;
        this.warnings = Collections.unmodifiableList(warnings);
        this.success = success;
    }

    public static SizeChartUploadResult of(UUID sizeChartId, int version, int entriesSaved,
                                           List<String> warnings) {
        return new SizeChartUploadResult(sizeChartId, version, entriesSaved, warnings, true);
    }

    public UUID getSizeChartId() { return sizeChartId; }
    public int getVersion()      { return version; }
    public int getEntriesSaved() { return entriesSaved; }
    public List<String> getWarnings() { return warnings; }
    public boolean isSuccess()   { return success; }
}
