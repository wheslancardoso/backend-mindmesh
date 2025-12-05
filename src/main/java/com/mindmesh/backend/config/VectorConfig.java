package com.mindmesh.backend.config;

import com.pgvector.PGvector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Configuração para habilitar suporte ao tipo VECTOR do PGVector no driver
 * JDBC.
 * O mapeamento Hibernate será feito depois, neste momento só precisamos
 * registrar o tipo.
 */
@Configuration
public class VectorConfig {

    @Bean
    public PGvector pgvectorType(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            PGvector.addVectorType(connection);
        }
        return new PGvector();
    }
}
