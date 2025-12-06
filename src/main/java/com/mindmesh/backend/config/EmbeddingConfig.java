package com.mindmesh.backend.config;

import com.mindmesh.backend.service.EmbeddingService;
import com.mindmesh.backend.service.MockEmbeddingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    @ConditionalOnMissingBean(EmbeddingService.class)
    public EmbeddingService fallbackEmbeddingService() {
        return new MockEmbeddingService();
    }
}
