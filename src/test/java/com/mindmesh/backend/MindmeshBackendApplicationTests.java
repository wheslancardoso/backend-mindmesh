package com.mindmesh.backend;

import com.mindmesh.backend.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MindmeshBackendApplicationTests {

	@MockBean
	private EmbeddingService embeddingService;

	@Test
	void contextLoads() {
	}

}
