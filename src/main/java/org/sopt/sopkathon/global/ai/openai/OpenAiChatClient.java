package org.sopt.sopkathon.global.ai.openai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiChatClient {
	private final RestClient client;
	private final ObjectMapper mapper;
	private final String apiKey;

	public OpenAiChatClient(ObjectMapper mapper, OpenAiProperties properties) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.getConnectTimeoutMs());
		factory.setReadTimeout(properties.getReadTimeoutMs());
		this.client = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
		this.mapper = mapper;
		this.apiKey = properties.getApiKey();
	}

	public <T> T complete(String model, String instructions, String input, String name,
		Map<String, Object> fields, Class<T> target, Map<String, Object> generationOptions) {
		if (apiKey.isBlank() || instructions.isBlank()) {
			throw unavailable();
		}
		Map<String, Object> schema = Map.of("type", "object", "properties", fields, "required", fields.keySet(),
			"additionalProperties", false);
		Map<String, Object> body = new HashMap<>(generationOptions);
		body.putAll(Map.of("model", model, "messages", List.of(
			Map.of("role", "developer", "content", instructions), Map.of("role", "user", "content", input)),
			"response_format", Map.of("type", "json_schema", "json_schema",
				Map.of("name", name, "strict", true, "schema", schema))));
		try {
			JsonNode response = client.post().uri("/chat/completions").headers(h -> h.setBearerAuth(apiKey))
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
			if (response == null || !"stop".equals(response.at("/choices/0/finish_reason").asString())) {
				throw unavailable();
			}
			JsonNode content = response.at("/choices/0/message/content");
			if (!content.isString()) {
				throw unavailable();
			}
			JsonNode value = mapper.readTree(content.asString());
			if (!value.isObject() || !fields.keySet().stream().allMatch(value::has)) {
				throw unavailable();
			}
			return mapper.treeToValue(value, target);
		} catch (JacksonException | RestClientException e) {
			throw unavailable();
		}
	}

	private static BusinessException unavailable() {
		return new BusinessException(ErrorCode.AI_UNAVAILABLE);
	}
}
