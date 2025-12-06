-- H2 não suporta o tipo VECTOR do PostgreSQL
-- Criar alias para permitir testes

-- Documentos
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT,
    text CLOB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Document Chunks (com float array ao invés de vector)
CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    content CLOB NOT NULL,
    embedding REAL ARRAY,
    metadata CLOB,
    token_count INTEGER,
    chunk_index INTEGER,
    FOREIGN KEY (document_id) REFERENCES documents(id)
);
