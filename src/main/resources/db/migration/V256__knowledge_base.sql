-- V256: M436 knowledge base

CREATE SEQUENCE IF NOT EXISTS selfservice.knowledge_article_no_seq START 1;

CREATE TABLE selfservice.knowledge_article (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    article_no VARCHAR(20),

    code VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary VARCHAR(2000),
    category VARCHAR(100) NOT NULL,
    tags VARCHAR(500),
    body TEXT NOT NULL,

    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT, PUBLISHED, ARCHIVED
    version INT NOT NULL DEFAULT 1,

    view_count INT NOT NULL DEFAULT 0,
    helpful_votes INT NOT NULL DEFAULT 0,
    not_helpful_votes INT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(255) NOT NULL,

    UNIQUE(tenant_id, code)
);

CREATE INDEX idx_knowledge_article_tenant_status ON selfservice.knowledge_article(tenant_id, status);
CREATE INDEX idx_knowledge_article_category ON selfservice.knowledge_article(tenant_id, category);
CREATE INDEX idx_knowledge_article_search ON selfservice.knowledge_article USING gin(to_tsvector('english', title || ' ' || summary || ' ' || body));

COMMENT ON TABLE selfservice.knowledge_article IS 'M436 — HR knowledge base articles (FAQs, policies, how-tos)';
COMMENT ON COLUMN selfservice.knowledge_article.article_no IS 'Generated article number KB-NNNNN';
COMMENT ON COLUMN selfservice.knowledge_article.status IS 'DRAFT visible to HR only; PUBLISHED readable by all authenticated';
COMMENT ON COLUMN selfservice.knowledge_article.helpful_votes IS 'Simple counter — no dedup table, noted in M436 design';
