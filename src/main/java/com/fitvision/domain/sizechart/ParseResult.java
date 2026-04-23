package com.fitvision.domain.sizechart;

import java.util.Collections;
import java.util.List;

/**
 * Value object representing the outcome of parsing a size chart file.
 *
 * <p>Use the static factory methods to construct instances:
 * <ul>
 *   <li>{@link #success(List, List, int)} — file was parsed; entries may be empty</li>
 *   <li>{@link #failure(String)} — file could not be parsed at all</li>
 * </ul>
 */
public final class ParseResult {

    private final List<SizeEntryData> entries;
    private final List<String> warnings;
    private final int skippedRows;
    private final boolean success;

    private ParseResult(List<SizeEntryData> entries, List<String> warnings, int skippedRows, boolean success) {
        this.entries = Collections.unmodifiableList(entries);
        this.warnings = Collections.unmodifiableList(warnings);
        this.skippedRows = skippedRows;
        this.success = success;
    }

    // -----------------------------------------------------------------------
    // Static factories
    // -----------------------------------------------------------------------

    /**
     * Creates a successful parse result.
     *
     * @param entries     the parsed size entries (may be empty if file had no data rows)
     * @param warnings    row-level warnings (non-fatal issues encountered during parsing)
     * @param skippedRows the number of data rows that were skipped due to missing or
     *                    invalid values
     */
    public static ParseResult success(List<SizeEntryData> entries, List<String> warnings, int skippedRows) {
        return new ParseResult(entries, warnings, skippedRows, true);
    }

    /**
     * Creates a failure result indicating that the file could not be parsed at all.
     *
     * @param reason human-readable explanation (added as the sole warning entry)
     */
    public static ParseResult failure(String reason) {
        return new ParseResult(Collections.emptyList(), List.of(reason), 0, false);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public List<SizeEntryData> getEntries() {
        return entries;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public boolean isSuccess() {
        return success;
    }
}
