package org.sopt.sopkathon.infrastructure.openai;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.sopkathon.domain.story.dto.request.PublishStoryRequest;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class OpenAiStoryOpeningGeneratorTest {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final PublishStoryRequest REQUEST = new PublishStoryRequest("최초 원문", "편집한 제목",
		"편집한 소개", "편집한 배경", "주인공은 토끼다.", "토끼가 열쇠를 찾는다.", StoryCategory.FANTASY);
	private MockRestServiceServer server;
	private OpenAiStoryOpeningGenerator generator;
	private OpenAiProperties properties;

	@BeforeEach
	void setUp() {
		var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
		server = MockRestServiceServer.bindTo(builder).build();
		properties = new OpenAiProperties();
		properties.setApiKey("test-key");
		properties.setModel("test-model");
		generator = new OpenAiStoryOpeningGenerator(new OpenAiResponsesClient(builder.build(), properties, JSON), JSON);
	}

	@Test
	void sendsOnlyFinalFieldsWithStrictOpeningSchemaAndBuildsOneMessage() {
		server.expect(requestTo("https://api.openai.com/v1/responses"))
			.andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer test-key"))
			.andExpect(request -> {
				var body = JSON.readTree(((MockClientHttpRequest)request).getBodyAsString());
				var input = JSON.readTree(body.path("input").stringValue());
				assertEquals(6, input.size());
				assertEquals(REQUEST.title(), input.path("title").stringValue());
				assertEquals(REQUEST.summary(), input.path("summary").stringValue());
				assertEquals(REQUEST.setting(), input.path("setting").stringValue());
				assertEquals(REQUEST.characters(), input.path("characters").stringValue());
				assertEquals(REQUEST.coreEvent(), input.path("coreEvent").stringValue());
				assertEquals(REQUEST.category().name(), input.path("category").stringValue());
				assertFalse(input.has("transcript"));
				assertFalse(body.path("store").booleanValue());
				var format = body.path("text").path("format");
				assertEquals("story_opening", format.path("name").stringValue());
				assertTrue(format.path("strict").booleanValue());
				assertEquals(2, format.path("schema").path("required").size());
				assertFalse(format.path("schema").path("additionalProperties").booleanValue());
			})
			.andRespond(withSuccess(envelope(opening("토끼는 여우와 숲에 들어섰다.", "어느 길로 갈까요?")), MediaType.APPLICATION_JSON));
		var result = generator.generate(REQUEST);
		assertEquals("토끼는 여우와 숲에 들어섰다.\n\n어느 길로 갈까요?", result.messageText());
		server.verify();
	}

	@Test
	void acceptsCodePointBoundaryAndFullWidthQuestionMark() {
		server.expect(anything()).andRespond(withSuccess(envelope(opening("🐰".repeat(300), "어디로 갈까요？")), MediaType.APPLICATION_JSON));
		assertEquals(600, generator.generate(REQUEST).scene().length());
		server.verify();
	}

	@ParameterizedTest
	@MethodSource("invalidOutput")
	void rejectsBadShapeBlankOverlongSceneAndAnythingOtherThanOneQuestion(String output) {
		server.expect(anything()).andRespond(withSuccess(envelope(output), MediaType.APPLICATION_JSON));
		assertUnavailable();
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(ints = {401, 429, 500, 503})
	void translatesHttpFailureWithoutLeakingProviderResponse(int status) {
		server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).body("private provider details"));
		assertUnavailable();
		server.verify();
	}

	@Test
	void translatesTimeoutWithoutRetry() {
		server.expect(anything()).andRespond(withException(new SocketTimeoutException("private details")));
		assertUnavailable();
		server.verify();
	}

	@Test
	void missingConfigurationDoesNotCallProvider() {
		properties.setApiKey(" ");
		assertUnavailable();
		server.verify();
	}

	private void assertUnavailable() {
		assertEquals(ErrorCode.AI_UNAVAILABLE,
			assertThrows(BusinessException.class, () -> generator.generate(REQUEST)).getErrorCode());
	}

	private static Stream<String> invalidOutput() {
		return Stream.of("{}", "{\"scene\":1,\"question\":\"어디로 갈까요?\"}",
			"{\"scene\":\"장면\",\"question\":null}",
			"{\"scene\":\"장면\",\"question\":\"어디로 갈까요?\",\"extra\":\"값\"}",
			opening(" ", "어디로 갈까요?"), opening("🐰".repeat(301), "어디로 갈까요?"),
			opening("누구일까?", "어디로 갈까요?"), opening("장면", " "),
			opening("장면", "질문 없이 끝남"), opening("장면", "어디로? 누구랑？"),
			opening("장면", "어디로? 다음 설명"), opening("장면", "질".repeat(300) + "?"));
	}

	private static String opening(String scene, String question) {
		return JSON.writeValueAsString(Map.of("scene", scene, "question", question));
	}

	private static String envelope(String output) {
		return JSON.writeValueAsString(Map.of("status", "completed", "output", List.of(
			Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", output))))));
	}
}
