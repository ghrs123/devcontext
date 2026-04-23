package com.fitvision.domain.sizechart;

/**
 * Immutable parsed representation of a single size entry row from a CSV or Excel upload.
 * This is NOT a JPA entity — it is purely data produced by the parsers and consumed by
 * the persistence layer.
 *
 * <p>All measurement fields are nullable: a blank cell in the source file becomes null here.
 * sizeLabel is always non-null, trimmed, and uppercased by the parser.
 */
public record SizeEntryData(
        String sizeLabel,
        Double chestMin,
        Double chestMax,
        Double waistMin,
        Double waistMax,
        Double hipMin,
        Double hipMax,
        Double heightMin,
        Double heightMax
) {
}
