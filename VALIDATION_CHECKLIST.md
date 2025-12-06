# ✅ MindMesh Backend - Checklist de Validação

## Pré-requisitos
- [ ] Docker Desktop instalado e rodando
- [ ] Java 21+ instalado
- [ ] Maven instalado

---

## 1. Docker rodando com PGVector

```bash
# Subir containers
docker-compose up -d

# Verificar status
docker-compose ps
```

**Validação:**
- [ ] Container `mindmesh_postgres` está `Up (healthy)`
- [ ] Container `mindmesh_pgadmin` está `Up`
- [ ] pgAdmin acessível em `http://localhost:5050`

```bash
# Testar conexão e extensão PGVector
docker exec -it mindmesh_postgres psql -U admin -d mindmesh_db -c "SELECT extname FROM pg_extension WHERE extname = 'vector';"
```

- [ ] Retorna `vector` confirmando extensão instalada

---

## 2. Classe DocumentChunk carregou sem erro

```bash
# Compilar projeto
./mvnw compile
```

**Validação:**
- [ ] Sem erros de import em `DocumentChunk.java`
- [ ] Anotações Lombok processadas (`@Data`, `@Builder`)
- [ ] `JsonNode` importado corretamente (`com.fasterxml.jackson.databind.JsonNode`)
- [ ] Console exibe `BUILD SUCCESS`

---

## 3. Hibernate reconheceu coluna VECTOR

```bash
# Iniciar aplicação
./mvnw spring-boot:run
```

**Validação no log:**
- [ ] Hibernate cria tabela `document_chunks`
- [ ] Coluna `embedding` criada com tipo `vector(1536)`
- [ ] Sem erros de tipo desconhecido (`unknown type: vector`)

**SQL esperado no log:**
```sql
create table document_chunks (
    id uuid not null,
    document_id uuid not null,
    content text not null,
    embedding vector(1536),
    metadata jsonb,
    token_count integer,
    chunk_index integer,
    primary key (id)
)
```

---

## 4. Repositório compilou sem erro

**Validação:**
- [ ] `DocumentChunkRepository.java` compila sem erros
- [ ] Query nativa `findSimilar()` parseada corretamente
- [ ] Método default (overload) funciona

```bash
# Testar compilação específica
./mvnw compile -pl . -am
```

- [ ] Sem erros em `@Query` nativa
- [ ] Console exibe `BUILD SUCCESS`

---

## 5. Application sobe sem falhas de conexão

```bash
./mvnw spring-boot:run
```

**Validação no log:**
- [ ] `HikariPool-1 - Started` (conexão pool ativa)
- [ ] `Tomcat started on port 8080`
- [ ] Sem `Connection refused` ou `FATAL: database does not exist`

**Teste de health:**
```bash
curl -s http://localhost:8080/actuator/health | jq .
```

- [ ] Status `UP` (se actuator estiver habilitado)

---

## Troubleshooting

| Problema | Solução |
|----------|---------|
| `Connection refused` | Verificar se Docker está rodando: `docker-compose ps` |
| `unknown type: vector` | Verificar se extensão pgvector está instalada no PostgreSQL |
| `relation "documents" does not exist` | Criar entidade `Document.java` (FK referenciada na query) |
| `HikariPool-1 - Connection is not available` | Verificar credenciais no `application.properties` |

---

## Resultado Final

| Item | Status |
|------|--------|
| Docker + PGVector | ⬜ |
| DocumentChunk | ⬜ |
| Hibernate VECTOR | ⬜ |
| Repository | ⬜ |
| Application Up | ⬜ |

**Legenda:** ✅ OK | ⬜ Pendente | ❌ Falhou
