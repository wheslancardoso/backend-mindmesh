package com.mindmesh.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração do Springdoc OpenAPI para documentação da API.
 * 
 * Endpoints expostos:
 * - /swagger-ui.html → Redireciona para Swagger UI
 * - /swagger-ui/index.html → Swagger UI
 * - /v3/api-docs → Especificação OpenAPI 3 em JSON
 * - /v3/api-docs.yaml → Especificação OpenAPI 3 em YAML
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI mindMeshOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Servidor de Desenvolvimento Local"),
                        new Server()
                                .url("https://api.mindmesh.io")
                                .description("Servidor de Produção")));
    }

    private Info apiInfo() {
        return new Info()
                .title("MindMesh API")
                .description("""
                        API do MindMesh – Backend para RAG (Retrieval-Augmented Generation).

                        Funcionalidades:
                        - Upload e processamento de documentos (PDF, DOCX, TXT)
                        - Geração de embeddings vetoriais via OpenAI
                        - Busca semântica com PGVector
                        - Chat com contexto aumentado (RAG)
                        """)
                .version("v1")
                .contact(new Contact()
                        .name("MindMesh Team")
                        .email("dev@mindmesh.io")
                        .url("https://github.com/mindmesh"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }
}
