-- ========================================
-- MindMesh AI - Schema Inicial PostgreSQL + PGVector
-- Este script será executado automaticamente pelo Docker
-- em /docker-entrypoint-initdb.d
-- ========================================

-- 0. Extensões necessárias
CREATE EXTENSION IF NOT EXISTS vector;
-- Se quiser gerar UUID no banco em vez da aplicação (opcional):
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ========================================
-- 1. Tabela: users  (Segurança e Multi-tenancy)
-- ========================================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER', -- USER / ADMIN
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Índice para busca rápida por email
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- ========================================
-- 2. Tabela: documents  (Ingestão + Deduplicação)
-- ========================================
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    filename VARCHAR(255) NOT NULL,
    file_hash VARCHAR(64) NOT NULL, -- SHA-256 do conteúdo
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / PROCESSING / COMPLETED
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

-- Cada usuário não pode reenviar o mesmo arquivo (por hash)
CREATE UNIQUE INDEX IF NOT EXISTS idx_documents_user_filehash
    ON documents(user_id, file_hash);

-- Índice para facilitar filtros por usuário
CREATE INDEX IF NOT EXISTS idx_documents_user_id
    ON documents(user_id);

-- Índice para filtros por status (ex: só COMPLETED)
CREATE INDEX IF NOT EXISTS idx_documents_status
    ON documents(status);

-- ========================================
-- 3. Tabela: document_chunks  (Memória Semântica - PGVector)
-- ========================================
CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding VECTOR(1536),      -- text-embedding-3-small
    metadata JSONB,
    token_count INTEGER,
    chunk_index INTEGER          -- ordem dentro do documento
);

-- Índice HNSW para busca vetorial (similaridade cosseno)
CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops);

-- Índice para JOINs por documento
CREATE INDEX IF NOT EXISTS idx_document_chunks_document_id
    ON document_chunks(document_id);

-- Opcional: índice GIN em metadata para buscas híbridas
CREATE INDEX IF NOT EXISTS idx_document_chunks_metadata_gin
    ON document_chunks
    USING gin (metadata);

-- ========================================
-- 4. Tabela: chat_sessions  (Sessões de conversa)
-- ========================================
CREATE TABLE IF NOT EXISTS chat_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_id
    ON chat_sessions(user_id);

-- ========================================
-- 5. Tabela: chat_messages  (Histórico + Feedback Loop)
-- ========================================
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,          -- USER / ASSISTANT / SYSTEM
    content TEXT NOT NULL,
    used_chunk_ids UUID[],              -- referência aos chunks usados no contexto
    feedback_score INTEGER,             -- ex: -1 / 0 / 1 ou 1..5
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id
    ON chat_messages(session_id);

-- Opcional: para facilitar auditoria por chunk usado
CREATE INDEX IF NOT EXISTS idx_chat_messages_used_chunk_ids_gin
    ON chat_messages
    USING gin (used_chunk_ids);

-- ========================================
-- 6. Notas de configuração PGVector (para usar depois, via SET)
-- ========================================
-- Em ambiente multi-tenant, recomenda-se:
--   SET hnsw.iterative_scan = 'relaxed_order';
-- Isso deve ser aplicado em nível de sessão ou banco, não aqui no schema.
