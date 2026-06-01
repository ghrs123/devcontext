-- Add source URL metadata for scraped charts
ALTER TABLE size_charts
    ADD COLUMN scrape_source_url TEXT;

-- Track scrape executions for global brands
CREATE TABLE scrape_jobs (
    id            UUID PRIMARY KEY,
    brand_id      UUID        NOT NULL REFERENCES brands(id),
    status        VARCHAR(20) NOT NULL,
    started_at    TIMESTAMP,
    completed_at  TIMESTAMP,
    pages_scraped INTEGER     NOT NULL DEFAULT 0,
    entries_found INTEGER     NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scrape_jobs_brand_created
    ON scrape_jobs (brand_id, created_at DESC);
