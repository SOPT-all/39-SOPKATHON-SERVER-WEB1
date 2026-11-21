package org.sopt.sopkathon.domain.story.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.sopkathon.domain.record.repository.RecordRepository;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;
import org.sopt.sopkathon.domain.story.repository.StoryRepository;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoryDraftApiTest {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final GenerateStoryDraftResponse DRAFT = new GenerateStoryDraftResponse(
		"숲속의 열쇠", "열쇠를 잃은 토끼가 여우를 만나요.", "해 질 무렵의 소나무 숲",
		"주인공은 토끼다. 여우가 길을 안내한다.", StoryCategory.FANTASY, "토끼가 열쇠를 찾는다.");
	@Autowired
	private MockMvc mvc;
	@Autowired
	private RecordRepository recordRepository;
	@MockitoSpyBean
	private StoryRepository storyRepository;
	@Autowired
	private StoryDraftWriter writer;
	@Autowired
	private JdbcTemplate jdbc;
	@MockitoBean
	private StoryDraftGenerator generator;

	@BeforeEach
	@AfterEach
	void cleanDatabase() {
		jdbc.update("delete from chat");
		jdbc.update("delete from context");
		storyRepository.deleteAll();
		recordRepository.deleteAll();
	}

	@Test
	void commitsOriginalAndDraftBeforeReturningSuccessAndKeepsAiOutsideTransaction() throws Exception {
		String original = "  토끼가 열쇠를 이러버렸어요. 여우와 함께 찾았어요.\n";
		when(generator.generate(original)).thenAnswer(invocation -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
			assertEquals(0, recordRepository.count());
			return DRAFT;
		});
		MvcResult result = send(original);
		assertEquals(200, result.getResponse().getStatus());
		JsonNode response = JSON.readTree(result.getResponse().getContentAsByteArray());
		JsonNode data = response.path("data");
		assertEquals("SUCCESS", response.path("code").stringValue());
		assertEquals(7, data.size());
		assertEquals(1, recordRepository.count());
		assertEquals(1, storyRepository.count());
		var record = recordRepository.findAll().getFirst();
		var story = storyRepository.findAll().getFirst();
		assertAll(
			() -> assertEquals(original, record.getText()),
			() -> assertEquals(UUID.fromString(record.getUuid()).toString(), data.path("recordUuid").stringValue()),
			() -> assertEquals(record.getId(), story.getRecord().getId()),
			() -> assertEquals(StoryStatus.DRAFT, story.getStatus()),
			() -> assertEquals(story.getTitle(), data.path("title").stringValue()),
			() -> assertEquals(story.getSummary(), data.path("summary").stringValue()),
			() -> assertEquals(story.getBackground(), data.path("setting").stringValue()),
			() -> assertEquals(story.getCharacter(), data.path("characters").stringValue()),
			() -> assertEquals(story.getCategory().name(), data.path("category").stringValue()),
			() -> assertEquals(story.getEvent(), data.path("coreEvent").stringValue()),
			() -> assertNull(story.getPrologue()),
			() -> assertNull(story.getImageUrl()),
			() -> assertEquals(0L, jdbc.queryForObject("select count(*) from chat", Long.class)),
			() -> assertEquals(0L, jdbc.queryForObject("select count(*) from context", Long.class))
		);
	}

	@Test
	void storesOriginalAtUnicodeCodePointBoundary() throws Exception {
		String original = "🐰".repeat(5000);
		when(generator.generate(original)).thenReturn(DRAFT);
		assertEquals(200, send(original).getResponse().getStatus());
		assertEquals(original, recordRepository.findAll().getFirst().getText());
	}

	@Test
	void repeatedTranscriptCreatesIndependentDrafts() throws Exception {
		when(generator.generate(anyString())).thenReturn(DRAFT);
		JsonNode first = JSON.readTree(send("같은 원문").getResponse().getContentAsByteArray());
		JsonNode second = JSON.readTree(send("같은 원문").getResponse().getContentAsByteArray());
		assertNotEquals(first.path("data").path("recordUuid").stringValue(),
			second.path("data").path("recordUuid").stringValue());
		assertEquals(2, storyRepository.count());
		assertEquals(2, recordRepository.count());
	}

	@Test
	void aiFailureLeavesNoRecords() throws Exception {
		when(generator.generate(anyString())).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE));
		MvcResult result = send("원문");
		assertEquals(503, result.getResponse().getStatus());
		assertEquals("AI_UNAVAILABLE", JSON.readTree(result.getResponse().getContentAsByteArray()).path("code").stringValue());
		assertEquals(0, recordRepository.count());
		assertEquals(0, storyRepository.count());
	}

	@Test
	void httpStorageFailureRollsBackRecordAndDoesNotReturnSuccess() throws Exception {
		when(generator.generate(anyString())).thenReturn(DRAFT);
		doThrow(new DataIntegrityViolationException("injected storage failure"))
			.when(storyRepository).save(any(Story.class));
		MvcResult result = send("최초 원문");
		assertEquals(500, result.getResponse().getStatus());
		assertEquals("INTERNAL_SERVER_ERROR",
			JSON.readTree(result.getResponse().getContentAsByteArray()).path("code").stringValue());
		assertEquals(0, recordRepository.count());
		assertEquals(0, storyRepository.count());
	}

	@Test
	void realStoryColumnViolationRollsBackAlreadyInsertedRecord() {
		var overlongDraft = new GenerateStoryDraftResponse("제".repeat(21), DRAFT.summary(), DRAFT.setting(),
			DRAFT.characters(), DRAFT.category(), DRAFT.coreEvent());
		assertThrows(DataIntegrityViolationException.class,
			() -> writer.save(UUID.randomUUID().toString(), "최초 원문", overlongDraft));
		assertEquals(0, recordRepository.count());
		assertEquals(0, storyRepository.count());
	}

	@Test
	void documentsStringInputAndCodePointLimitInOpenApi() throws Exception {
		MvcResult result = mvc.perform(get("/v3/api-docs")).andReturn();
		assertEquals(200, result.getResponse().getStatus());
		JsonNode document = JSON.readTree(result.getResponse().getContentAsByteArray());
		JsonNode transcript = document.path("components").path("schemas").path("CreateStoryDraftRequest")
			.path("properties").path("transcript");
		assertEquals("string", transcript.path("type").stringValue());
		assertEquals(5000, transcript.path("maxLength").intValue());
	}

	private MvcResult send(String transcript) throws Exception {
		return mvc.perform(post("/api/v1/story-drafts").contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(Map.of("transcript", transcript)))).andReturn();
	}
}
