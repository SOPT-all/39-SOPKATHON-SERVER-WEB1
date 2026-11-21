package org.sopt.sopkathon.infrastructure.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OpenAiStoryDraftGeneratorTest {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String DRAFT = """
		{"title":"숲속의 열쇠","summary":"열쇠를 잃은 토끼가 여우를 만나요.",
		"setting":"해 질 무렵 소나무 숲","characters":"주인공은 토끼다. 여우는 길을 안내한다.",
		"category":"FANTASY","coreEvent":"토끼가 잃어버린 열쇠를 찾는다."}
		""";
	private MockRestServiceServer server;
	private OpenAiProperties properties;
	private OpenAiStoryDraftGenerator generator;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
		server = MockRestServiceServer.bindTo(builder).build();
		properties = new OpenAiProperties();
		properties.setApiKey("test-key");
		properties.setModel("test-model");
		generator = new OpenAiStoryDraftGenerator(new OpenAiResponsesClient(builder.build(), properties, JSON));
	}

	@Test
	void sendsOriginalTranscriptAndStrictSchemaAndParsesOutput() {
		String transcript = "  토끼가 열쇠를 이러버렸어요.\n";
		server.expect(requestTo("https://api.openai.com/v1/responses"))
			.andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer test-key"))
			.andExpect(request -> {
				JsonNode body = JSON.readTree(((MockClientHttpRequest)request).getBodyAsString());
				assertEquals(transcript, body.path("input").stringValue());
				assertEquals("test-model", body.path("model").stringValue());
				assertFalse(body.path("store").booleanValue());
				JsonNode format = body.path("text").path("format");
				assertEquals("json_schema", format.path("type").stringValue());
				assertTrue(format.path("strict").booleanValue());
				assertFalse(format.path("schema").path("additionalProperties").booleanValue());
				assertEquals(6, format.path("schema").path("required").size());
				assertEquals(6, format.path("schema").path("properties").path("category").path("enum").size());
			})
			.andRespond(withSuccess(envelope(DRAFT), MediaType.APPLICATION_JSON));

		var result = generator.generate(transcript);
		assertEquals("숲속의 열쇠", result.title());
		assertEquals(StoryCategory.FANTASY, result.category());
		assertEquals("토끼가 잃어버린 열쇠를 찾는다.", result.coreEvent());
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(strings = {"key", "model"})
	void rejectsMissingConfigurationWithoutHttpRequest(String missing) {
		if (missing.equals("key")) {
			properties.setApiKey(" \t");
		} else {
			properties.setModel("");
		}
		assertUnavailable();
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(ints = {401, 429, 500, 503})
	void translatesProviderHttpErrors(int status) {
		server.expect(requestTo("https://api.openai.com/v1/responses"))
			.andRespond(withStatus(HttpStatus.valueOf(status)).body("private provider details"));
		assertUnavailable();
		server.verify();
	}

	@Test
	void translatesTimeoutWithoutRetry() {
		server.expect(requestTo("https://api.openai.com/v1/responses"))
			.andRespond(withException(new SocketTimeoutException("timeout")));
		assertUnavailable();
		server.verify();
	}

	@ParameterizedTest
	@MethodSource("invalidResponses")
	void rejectsIncompleteRefusedOrMalformedResponses(String response) {
		server.expect(requestTo("https://api.openai.com/v1/responses"))
			.andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
		assertUnavailable();
		server.verify();
	}

	private void assertUnavailable() {
		BusinessException error = assertThrows(BusinessException.class, () -> generator.generate("원문"));
		assertEquals(ErrorCode.AI_UNAVAILABLE, error.getErrorCode());
		assertEquals("이야기 AI에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", error.getMessage());
	}

	private static Stream<String> invalidResponses() {
		return Stream.of("not json", "null", "{}", "", "{\"status\":\"incomplete\",\"output\":[]}",
			"{\"status\":\"completed\",\"output\":[]}",
			JSON.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of("type", "message",
				"content", List.of(Map.of("type", "refusal", "refusal", "refused")))))),
			envelope("{}"), envelope(DRAFT.replace("FANTASY", "UNKNOWN")),
			envelope(DRAFT.replace("\"숲속의 열쇠\"", "123")),
			envelope(DRAFT.replace("\"숲속의 열쇠\"", "\" \"")),
			envelope(DRAFT + " {}"), envelope(DRAFT) + " {}",
			JSON.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of("type", "message",
				"content", Map.of("unexpected", Map.of("type", "output_text", "text", DRAFT)))))));
	}

	private static String envelope(String text) {
		return JSON.writeValueAsString(Map.of("status", "completed", "output", List.of(
			Map.of("type", "reasoning", "summary", List.of()),
			Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", text))))));
	}
}
