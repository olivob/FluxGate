package com.bryan.fluxgate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bryan.fluxgate.service.ApiKeyHashingService;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.bryan.fluxgate.support.PostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class FluxgateApplicationTests extends PostgresIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper objectMapper;
	@Autowired ApiKeyHashingService hashing;

	@Test
	void contextLoads() {
	}

	@Test
	void realChatResponseAndStoredLogShareOneRequestId() throws Exception {
		UUID accountId = UUID.randomUUID();
		UUID keyId = UUID.randomUUID();
		String rawKey = "test-" + UUID.randomUUID();
		jdbc.update("insert into accounts (id, name) values (?, ?)", accountId, accountId.toString());
		jdbc.update("insert into api_keys (id, account_id, key_hash, key_prefix) values (?, ?, ?, ?)",
				keyId, accountId, hashing.hash(rawKey), "test");

		var result = mvc.perform(post("/v1/chat/completions").header("X-API-Key", rawKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"model\":\"mock-model\",\"prompt\":\"Hello\"}"))
				.andExpect(status().isOk()).andReturn();
		String requestId = result.getResponse().getHeader("X-Request-ID");
		assertEquals(requestId, objectMapper.readTree(result.getResponse().getContentAsString()).get("requestId").asText());
		var log = jdbc.queryForMap("select * from api_request_logs where id = ?", UUID.fromString(requestId));
		assertEquals(keyId, log.get("api_key_id"));
		assertEquals(accountId, log.get("account_id"));
		assertEquals(200, log.get("status_code"));
		assertEquals("mock-provider", log.get("provider"));
		assertEquals("mock-model", log.get("model"));
		assertNull(log.get("error_code"));
	}

	@Test
	void actuatorHealthIsPublicEvenWithAnInvalidKey() throws Exception {
		mvc.perform(get("/actuator/health").header("X-API-Key", "invalid-key"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void actuatorMetricsRequireAuthentication() throws Exception {
		mvc.perform(get("/actuator/metrics"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("unauthorized"));
	}

}
