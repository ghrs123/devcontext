package com.fitvision.domain.sizechart;

import java.io.InputStream;

/**
 * Strategy interface for size chart file parsers.
 *
 * <p>Implementations must:
 * <ul>
 *   <li>Never throw unchecked exceptions on bad data — return {@link ParseResult#failure(String)}
 *       or skip the offending row instead.</li>
 *   <li>Not close the supplied {@link InputStream} — that is the caller's responsibility.</li>
 * </ul>
 */
public interface SizeChartFileParser {

    /**
     * Parses the given input stream and returns a {@link ParseResult}.
     *
     * @param inputStream the raw file bytes; caller is responsible for closing this stream
     * @return a parse result — never null
     */
    ParseResult parse(InputStream inputStream);
}
