package com.mindmesh.backend.config;

import com.pgvector.PGvector;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.java.FloatArrayTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Configuração para habilitar suporte ao tipo VECTOR do PGVector no Hibernate.
 * Permite mapping automático de float[] para coluna VECTOR(1536).
 */
@Configuration
public class VectorConfig {

    /**
     * Registra o tipo PGvector na conexão do DataSource.
     * Necessário para que o driver JDBC reconheça o tipo VECTOR.
     */
    @Bean
    public PGvector pgvectorType(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            PGvector.addVectorType(connection);
        }
        return new PGvector();
    }
}
