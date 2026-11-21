package org.sopt.sopkathon.infrastructure.openai;

import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.validation.StoryDraftText;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class OpenAiResponsesClient {
	private final RestClient client;
	private final OpenAiProperties properties;
	private final ObjectMapper objectMapper;

	public JsonNode generate(String instructions, String input, String name, Map<String, Object> schema, Set<String> fields) {
		if (!StoryDraftText.hasText(properties.getApiKey()) || !StoryDraftText.hasText(properties.getModel())) {
			throw unavailable();
		}
		try {
			String body = client.post().uri("/responses")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("model", properties.getModel(), "instructions", instructions, "input", input,
					"store", false, "max_output_tokens", properties.getMaxOutputTokens(), "text", Map.of("format",
						Map.of("type", "json_schema", "name", name, "strict", true, "schema", schema))))
				.retrieve().body(String.class);
			return parse(body, fields);
		} catch (RestClientException | JacksonException | IllegalArgumentException e) {
			throw unavailable();
		}
	}

	private JsonNode parse(String body, Set<String> fields) {
		if (body == null || body.isBlank()) {
			throw unavailable();
		}
		JsonNode root = read(body);
		if (root == null || !"completed".equals(root.path("status").asString("")) || !root.path("output").isArray()) {
			throw unavailable();
		}
		StringBuilder text = new StringBuilder();
		for (JsonNode output : root.path("output")) {
			if (!"message".equals(output.path("type").asString(""))) {
				continue;
			}
			if (!output.path("content").isArray()) {
				throw unavailable();
			}
			for (JsonNode content : output.path("content")) {
				String type = content.path("type").asString("");
				if ("refusal".equals(type)) {
					throw unavailable();
				}
				if ("output_text".equals(type) && content.path("text").isString()) {
					text.append(content.path("text").stringValue());
				}
			}
		}
		if (text.isEmpty()) {
			throw unavailable();
		}
		JsonNode result = read(text.toString());
		if (result == null || !result.isObject() || !Set.copyOf(result.propertyNames()).equals(fields)) {
			throw unavailable();
		}
		for (String field : fields) {
			if (!result.path(field).isString()) {
				throw unavailable();
			}
		}
		return result;
	}

	private JsonNode read(String body) {
		return objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
	}

	private BusinessException unavailable() {
		return new BusinessException(ErrorCode.AI_UNAVAILABLE);
	}
}
