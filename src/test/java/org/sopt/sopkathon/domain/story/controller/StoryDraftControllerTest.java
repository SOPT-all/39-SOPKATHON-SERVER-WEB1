package org.sopt.sopkathon.domain.story.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.sopt.sopkathon.domain.story.dto.response.CreateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.domain.story.service.StoryDraftGenerator;
import org.sopt.sopkathon.domain.story.service.StoryDraftService;
import org.sopt.sopkathon.domain.story.service.StoryDraftWriter;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.exception.GlobalExceptionHandler;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class StoryDraftControllerTest {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final GenerateStoryDraftResponse DRAFT = new GenerateStoryDraftResponse(
		"숲속의 열쇠", "열쇠를 잃은 토끼가 여우를 만나요.", "해 질 무렵의 소나무 숲",
		"주인공은 토끼다. 여우가 길을 안내한다.", StoryCategory.FANTASY, "토끼가 열쇠를 찾는다.");
	private StoryDraftGenerator generator;
	private StoryDraftWriter writer;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		generator = mock(StoryDraftGenerator.class);
		writer = mock(StoryDraftWriter.class);
		mvc = MockMvcBuilders.standaloneSetup(new StoryDraftController(new StoryDraftService(generator, writer)))
			.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void acceptsUnauthenticatedJsonAndPreservesOriginalTranscript() throws Exception {
		String original = "  토끼가 열쇠를 이러버렸어요.\n";
		stubSuccess();
		MvcResult result = sendJson(JSON.writeValueAsString(Map.of("transcript", original)));
		assertEquals(200, result.getResponse().getStatus());
		var body = JSON.readTree(result.getResponse().getContentAsByteArray());
		assertEquals("SUCCESS", body.path("code").stringValue());
		assertEquals("요청에 성공했습니다.", body.path("message").stringValue());
		assertEquals("FANTASY", body.path("data").path("category").stringValue());
		verify(generator).generate(original);
		ArgumentCaptor<String> uuid = ArgumentCaptor.forClass(String.class);
		verify(writer).save(uuid.capture(), eq(original), eq(DRAFT));
		assertNotNull(UUID.fromString(uuid.getValue()));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{", "[]", "null", "{\"transcript\":123}", "{\"transcript\":true}",
		"{\"transcript\":[]}", "{\"transcript\":{}}"})
	void rejectsMalformedJsonAndNonStringValues(String json) throws Exception {
		assertError(sendJson(json), 400, "INVALID_REQUEST_BODY");
		verifyNoInteractions(generator, writer);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t\n", "\u00a0", "\u3000"})
	void rejectsMissingOrBlankTranscript(String transcript) throws Exception {
		String json = transcript == null ? "{\"transcript\":null}"
			: JSON.writeValueAsString(Map.of("transcript", transcript));
		assertError(sendJson(json), 400, "INVALID_INPUT");
		verifyNoInteractions(generator, writer);
	}

	@Test
	void rejectsAbsentTranscript() throws Exception {
		assertError(sendJson("{}"), 400, "INVALID_INPUT");
		verifyNoInteractions(generator, writer);
	}

	@Test
	void acceptsFiveThousandCodePointsDespiteLongerUtf16Length() throws Exception {
		String original = "🐰".repeat(5000);
		assertEquals(10000, original.length());
		stubSuccess();
		assertEquals(200, sendJson(JSON.writeValueAsString(Map.of("transcript", original))).getResponse().getStatus());
		verify(generator).generate(original);
	}

	@Test
	void rejectsFiveThousandAndOneCodePoints() throws Exception {
		assertError(sendJson(JSON.writeValueAsString(Map.of("transcript", "🐰".repeat(5001)))), 400, "INVALID_INPUT");
		verifyNoInteractions(generator, writer);
	}

	@ParameterizedTest
	@ValueSource(strings = {"text/plain", "multipart/form-data"})
	void rejectsUnsupportedMediaTypes(String contentType) throws Exception {
		assertError(mvc.perform(post("/api/v1/story-drafts").contentType(contentType).content("원문")).andReturn(),
			415, "UNSUPPORTED_MEDIA_TYPE");
		verifyNoInteractions(generator, writer);
	}

	@Test
	void returnsAiUnavailableWithoutSaving() throws Exception {
		when(generator.generate(anyString())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));
		assertError(sendJson("{\"transcript\":\"원문\"}"), 503, "AI_UNAVAILABLE");
		verifyNoInteractions(writer);
	}

	@Test
	void rejectsInvalidGeneratedDraftBeforeSaving() throws Exception {
		when(generator.generate(anyString())).thenReturn(new GenerateStoryDraftResponse(
			" ", "소개", "배경", "인물", StoryCategory.FANTASY, "사건"));
		assertError(sendJson("{\"transcript\":\"원문\"}"), 503, "AI_UNAVAILABLE");
		verifyNoInteractions(writer);
	}

	@Test
	void databaseFailureIsNotReportedAsAiFailureOrSuccess() throws Exception {
		when(generator.generate(anyString())).thenReturn(DRAFT);
		when(writer.save(anyString(), anyString(), any())).thenThrow(new DataIntegrityViolationException("test failure"));
		assertError(sendJson("{\"transcript\":\"원문\"}"), 500, "INTERNAL_SERVER_ERROR");
	}

	private MvcResult sendJson(String json) throws Exception {
		return mvc.perform(post("/api/v1/story-drafts").contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
	}

	private void assertError(MvcResult result, int status, String code) {
		assertEquals(status, result.getResponse().getStatus());
		var body = JSON.readTree(result.getResponse().getContentAsByteArray());
		assertEquals(code, body.path("code").stringValue());
		assertTrue(body.path("data").isNull());
	}

	private void stubSuccess() {
		when(generator.generate(anyString())).thenReturn(DRAFT);
		when(writer.save(anyString(), anyString(), any())).thenAnswer(invocation ->
			new CreateStoryDraftResponse(invocation.getArgument(0), DRAFT.title(), DRAFT.summary(), DRAFT.setting(),
				DRAFT.characters(), DRAFT.category(), DRAFT.coreEvent()));
	}
}
