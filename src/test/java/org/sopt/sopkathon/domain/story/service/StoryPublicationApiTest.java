package org.sopt.sopkathon.domain.story.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.sopt.sopkathon.domain.chat.entity.ChatType;
import org.sopt.sopkathon.domain.chat.repository.ChatRepository;
import org.sopt.sopkathon.domain.record.repository.RecordRepository;
import org.sopt.sopkathon.domain.story.dto.request.PublishStoryRequest;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryOpeningResponse;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoryPublicationApiTest {
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final PublishStoryRequest REQUEST = new PublishStoryRequest("  최초 원문\n", "편집한 제목",
		"편집한 소개", "편집한 배경", "주인공은 토끼다.", "토끼가 열쇠를 찾는다.", StoryCategory.FANTASY);
	private static final GenerateStoryOpeningResponse OPENING = new GenerateStoryOpeningResponse(
		"토끼가 숲에서 여우와 마주쳤다.", "토끼는 어느 길로 가면 좋을까요?");
	@Autowired private MockMvc mvc;
	@Autowired private StoryDraftWriter drafts;
	@Autowired private StoryPublicationService service;
	@Autowired private StoryPublicationWriter writer;
	@Autowired private StoryRepository stories;
	@Autowired private RecordRepository records;
	@Autowired private PublicationProperties properties;
	@Autowired private JdbcTemplate jdbc;
	@MockitoSpyBean private ChatRepository chats;
	@MockitoSpyBean private PublicationCoordinator coordinator;
	@MockitoBean private StoryOpeningGenerator generator;
	@MockitoBean private Clock clock;
	private final AtomicReference<Instant> now = new AtomicReference<>();
	private String uuid;
	private Long storyId;
	private Long recordId;

	@BeforeEach
	void setUp() {
		jdbc.update("delete from chat");
		jdbc.update("delete from context");
		stories.deleteAll();
		records.deleteAll();
		now.set(Instant.parse("2026-11-22T00:00:00Z"));
		when(clock.instant()).thenAnswer(invocation -> now.get());
		uuid = UUID.randomUUID().toString();
		drafts.save(uuid, REQUEST.transcript(), new GenerateStoryDraftResponse(
			"초안 제목", "초안 소개", "초안 배경", "초안 인물", StoryCategory.ETC, "초안 사건"));
		storyId = stories.findAll().getFirst().getId();
		recordId = records.findAll().getFirst().getId();
		when(generator.generate(any())).thenAnswer(invocation -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
			assertEquals(REQUEST, invocation.getArgument(0));
			return OPENING;
		});
	}

	@Test
	void publishesExistingDraftAtomicallyAndReplaysReorderedBodyWithoutGeneratingAgain() throws Exception {
		var first = send(uuid.toUpperCase(), REQUEST);
		assertSuccess(first, 201, "CREATED");
		var message = chats.findAll().getFirst();
		assertEquals(ChatType.STORY, message.getType());
		assertEquals(OPENING.messageText(), message.getText());
		assertEquals(storyId, message.getStory().getId());
		Story stored = story();
		assertAll(() -> assertEquals(StoryStatus.PUBLISHED, stored.getStatus()),
			() -> assertEquals(REQUEST.title(), stored.getTitle()),
			() -> assertEquals(REQUEST.summary(), stored.getSummary()),
			() -> assertEquals(REQUEST.setting(), stored.getBackground()),
			() -> assertEquals(REQUEST.characters(), stored.getCharacter()),
			() -> assertEquals(REQUEST.coreEvent(), stored.getEvent()),
			() -> assertEquals(REQUEST.category(), stored.getCategory()),
			() -> assertEquals(OPENING.scene(), stored.getPrologue()),
			() -> assertEquals(REQUEST.requestHash(), stored.getPublishRequestHash()),
			() -> assertNull(stored.getPublishAttemptToken()),
			() -> assertNull(stored.getPublishAttemptExpiresAt()));
		var original = records.findById(recordId).orElseThrow();
		assertEquals(REQUEST.transcript(), original.getText());
		assertEquals(uuid, original.getUuid());
		assertEquals(recordId, stored.getRecord().getId());
		assertEquals(1, stories.count());
		assertEquals(1, records.count());
		var fields = JSON.readTree(body(REQUEST));
		var reordered = new LinkedHashMap<String, Object>();
		for (String field : List.of("category", "coreEvent", "characters", "setting", "summary", "title", "transcript")) {
			reordered.put(field, fields.get(field));
		}
		assertSuccess(sendJson(uuid, JSON.writeValueAsString(reordered)), 200, "SUCCESS");
		assertEquals(message.getId(), chats.findAll().getFirst().getId());
		assertEquals(1, chats.count());
		verify(generator, times(1)).generate(REQUEST);
	}

	@Test
	void sameConcurrentHttpRequestsGenerateOnceAndWaiterReadsCommittedResult() throws Exception {
		var entered = new CountDownLatch(1);
		var finish = new CountDownLatch(1);
		var observed = observeWaiter();
		when(generator.generate(REQUEST)).thenAnswer(invocation -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
			entered.countDown();
			await(finish);
			return OPENING;
		});
		try (var pool = Executors.newFixedThreadPool(2)) {
			try {
				var owner = pool.submit(() -> send(uuid, REQUEST));
				await(entered);
				var waiter = pool.submit(() -> send(uuid, REQUEST));
				await(observed);
				assertEquals(StoryStatus.DRAFT, story().getStatus());
				assertEquals(0, chats.count());
				finish.countDown();
				assertSuccess(owner.get(5, TimeUnit.SECONDS), 201, "CREATED");
				assertSuccess(waiter.get(5, TimeUnit.SECONDS), 200, "SUCCESS");
			} finally {
				finish.countDown();
			}
		}
		verify(generator, times(1)).generate(REQUEST);
		assertEquals(1, chats.count());
	}

	@Test
	void differentConcurrentBodyConflictsWithoutSecondGeneration() throws Exception {
		var entered = new CountDownLatch(1);
		var finish = new CountDownLatch(1);
		when(generator.generate(REQUEST)).thenAnswer(invocation -> {
			entered.countDown();
			await(finish);
			return OPENING;
		});
		try (var pool = Executors.newSingleThreadExecutor()) {
			try {
				var owner = pool.submit(() -> send(uuid, REQUEST));
				await(entered);
				assertError(send(uuid, changedOriginal()), 409, "IDEMPOTENCY_KEY_CONFLICT");
				finish.countDown();
				assertSuccess(owner.get(5, TimeUnit.SECONDS), 201, "CREATED");
			} finally {
				finish.countDown();
			}
		}
		verify(generator, times(1)).generate(REQUEST);
	}

	@Test
	void aiFailureLeavesDraftUnchangedAndSameKeyCanRetry() throws Exception {
		when(generator.generate(REQUEST)).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE)).thenReturn(OPENING);
		assertError(send(uuid, REQUEST), 503, "AI_UNAVAILABLE");
		assertUntouchedDraft();
		assertSuccess(send(uuid, REQUEST), 201, "CREATED");
		assertEquals(1, chats.count());
	}

	@Test
	void databaseFailureRollsBackAllFinalFieldsAndMessageThenRetrySucceeds() throws Exception {
		doThrow(new DataIntegrityViolationException("injected save failure")).when(chats).save(any(Chat.class));
		assertError(send(uuid, REQUEST), 500, "INTERNAL_SERVER_ERROR");
		assertUntouchedDraft();
		reset(chats);
		assertSuccess(send(uuid, REQUEST), 201, "CREATED");
		assertEquals(1, chats.count());
	}

	@Test
	void actualDatabaseConstraintFailureRollsBackStoryUpdateAndPreservesClaimForOwnerCleanup() {
		var claim = coordinator.acquire(uuid, REQUEST, REQUEST.requestHash());
		assertThrows(DataIntegrityViolationException.class, () -> writer.publish(uuid, REQUEST.requestHash(), claim.token(),
			REQUEST, new GenerateStoryOpeningResponse("장".repeat(301), "어디로 갈까요?")));
		assertEquals(StoryStatus.DRAFT, story().getStatus());
		assertEquals("초안 제목", story().getTitle());
		assertEquals(0, chats.count());
		assertEquals(claim.token(), coordinator.observe(uuid).token());
		coordinator.release(uuid, claim.token());
		assertUntouchedDraft();
	}

	@Test
	void waiterReturns503AfterOwnerFailureWithoutAutomaticallyGeneratingAgain() throws Exception {
		var entered = new CountDownLatch(1);
		var finish = new CountDownLatch(1);
		var observed = observeWaiter();
		when(generator.generate(REQUEST)).thenAnswer(invocation -> {
			entered.countDown();
			await(finish);
			throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
		});
		try (var pool = Executors.newFixedThreadPool(2)) {
			try {
				var owner = pool.submit(() -> send(uuid, REQUEST));
				await(entered);
				var waiter = pool.submit(() -> send(uuid, REQUEST));
				await(observed);
				finish.countDown();
				assertError(owner.get(5, TimeUnit.SECONDS), 503, "AI_UNAVAILABLE");
				assertError(waiter.get(5, TimeUnit.SECONDS), 503, "AI_UNAVAILABLE");
			} finally {
				finish.countDown();
			}
		}
		verify(generator, times(1)).generate(REQUEST);
		assertUntouchedDraft();
	}

	@Test
	void expiredOwnerCannotOverwriteNewPublicationEvenWithOldEntityInHttpPersistenceContext() throws Exception {
		var entered = new CountDownLatch(1);
		var finish = new CountDownLatch(1);
		when(generator.generate(REQUEST)).thenAnswer(invocation -> {
			entered.countDown();
			await(finish);
			return new GenerateStoryOpeningResponse("이전 시도의 장면", "이전 질문?");
		}).thenReturn(OPENING);
		try (var pool = Executors.newSingleThreadExecutor()) {
			try {
				var old = pool.submit(() -> send(uuid, REQUEST));
				await(entered);
				now.set(coordinator.observe(uuid).expiresAt());
				assertSuccess(send(uuid, REQUEST), 201, "CREATED");
				finish.countDown();
				assertError(old.get(5, TimeUnit.SECONDS), 503, "AI_UNAVAILABLE");
			} finally {
				finish.countDown();
			}
		}
		assertEquals(OPENING.scene(), story().getPrologue());
		assertEquals(OPENING.messageText(), chats.findAll().getFirst().getText());
		assertEquals(1, chats.count());
		assertSuccess(send(uuid, REQUEST), 200, "SUCCESS");
		verify(generator, times(2)).generate(REQUEST);
	}

	@Test
	void staleHttpOwnerFailureCannotReleaseNewInProgressToken() throws Exception {
		var entered = new CountDownLatch(1);
		var finish = new CountDownLatch(1);
		when(generator.generate(REQUEST)).thenAnswer(invocation -> {
			entered.countDown();
			await(finish);
			throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
		});
		try (var pool = Executors.newSingleThreadExecutor()) {
			try {
				var old = pool.submit(() -> send(uuid, REQUEST));
				await(entered);
				now.set(coordinator.observe(uuid).expiresAt());
				var fresh = coordinator.acquire(uuid, REQUEST, REQUEST.requestHash());
				finish.countDown();
				assertError(old.get(5, TimeUnit.SECONDS), 503, "AI_UNAVAILABLE");
				assertEquals(fresh.token(), coordinator.observe(uuid).token());
				writer.publish(uuid, REQUEST.requestHash(), fresh.token(), REQUEST, OPENING);
			} finally {
				finish.countDown();
			}
		}
		assertEquals(1, chats.count());
	}

	@Test
	void finiteWaitReturns503AndLeavesOwnerClaimUntouched() throws Exception {
		var claim = coordinator.acquire(uuid, REQUEST, REQUEST.requestHash());
		Duration originalWait = properties.getWaitTimeout();
		Duration originalPoll = properties.getPollInterval();
		try {
			properties.setWaitTimeout(Duration.ofMillis(25));
			properties.setPollInterval(Duration.ofMillis(5));
			assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertError(send(uuid, REQUEST), 503, "AI_UNAVAILABLE"));
			assertEquals(claim.token(), coordinator.observe(uuid).token());
			verifyNoInteractions(generator);
		} finally {
			properties.setWaitTimeout(originalWait);
			properties.setPollInterval(originalPoll);
		}
	}

	@Test
	void interruptedWaitRestoresInterruptFlagWithoutReleasingOwner() {
		var claim = coordinator.acquire(uuid, REQUEST, REQUEST.requestHash());
		try {
			Thread.currentThread().interrupt();
			assertEquals(ErrorCode.AI_UNAVAILABLE, assertThrows(BusinessException.class,
				() -> service.publish(uuid, REQUEST)).getErrorCode());
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
		assertEquals(claim.token(), coordinator.observe(uuid).token());
		verifyNoInteractions(generator);
	}

	@Test
	void rejectsDifferentOriginalBeforeClaimAndDifferentPublishedBodyAsConflict() throws Exception {
		assertError(send(uuid, changedOriginal()), 400, "INVALID_INPUT");
		assertUntouchedDraft();
		assertSuccess(send(uuid, REQUEST), 201, "CREATED");
		assertError(send(uuid, changedOriginal()), 409, "IDEMPOTENCY_KEY_CONFLICT");
		verify(generator, times(1)).generate(REQUEST);
	}

	@Test
	void validUnknownUuidReturns404WithoutCallingAi() throws Exception {
		assertError(send(UUID.randomUUID().toString(), REQUEST), 404, "RESOURCE_NOT_FOUND");
		verifyNoInteractions(generator);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "not-a-uuid", "1-1-1-1-1"})
	void rejectsInvalidHeader(String key) throws Exception {
		assertError(send(key, REQUEST), 400, "INVALID_PARAMETER");
		verifyNoInteractions(generator);
	}

	@Test
	void rejectsMissingHeader() throws Exception {
		assertError(mvc.perform(post("/api/v1/stories").contentType(MediaType.APPLICATION_JSON).content(body(REQUEST))).andReturn(),
			400, "INVALID_PARAMETER");
		verifyNoInteractions(generator);
	}

	@ParameterizedTest
	@ValueSource(strings = {"{", "[]", "null", "{\"transcript\":123}", "{\"transcript\":true}",
		"{\"transcript\":[]}", "{\"transcript\":{}}"})
	void rejectsMalformedBodyAndNonStringValues(String json) throws Exception {
		assertError(sendJson(uuid, json), 400, "INVALID_REQUEST_BODY");
		verifyNoInteractions(generator);
	}

	@Test
	void rejectsMissingBlankAndInvalidCategoryFields() throws Exception {
		assertError(sendJson(uuid, "{}"), 400, "INVALID_INPUT");
		assertError(sendJson(uuid, body(REQUEST).replace("편집한 제목", " ")), 400, "INVALID_INPUT");
		assertError(sendJson(uuid, body(REQUEST).replace("FANTASY", "fantasy")), 400, "INVALID_INPUT");
		verifyNoInteractions(generator);
	}

	@Test
	void rejectsUnsupportedContentType() throws Exception {
		assertError(mvc.perform(post("/api/v1/stories").header("Idempotency-Key", uuid)
			.contentType(MediaType.TEXT_PLAIN).content(body(REQUEST))).andReturn(), 415, "UNSUPPORTED_MEDIA_TYPE");
		verifyNoInteractions(generator);
	}

	@Test
	void rejectsInvalidGeneratedOpeningBeforeWriting() throws Exception {
		when(generator.generate(REQUEST)).thenReturn(new GenerateStoryOpeningResponse("장면", "질문이 아님"));
		assertError(send(uuid, REQUEST), 503, "AI_UNAVAILABLE");
		assertUntouchedDraft();
	}

	@Test
	void cleanupFailureDoesNotHideAiFailureAndExpiredClaimCanBeRecovered() throws Exception {
		when(generator.generate(REQUEST)).thenThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE)).thenReturn(OPENING);
		doThrow(new DataIntegrityViolationException("injected cleanup failure"))
			.when(coordinator).release(eq(uuid), anyString());
		assertError(send(uuid, REQUEST), 503, "AI_UNAVAILABLE");
		assertEquals(StoryStatus.DRAFT, story().getStatus());
		assertEquals("초안 제목", story().getTitle());
		assertEquals(0, chats.count());
		var abandoned = coordinator.observe(uuid);
		assertNotNull(abandoned.token());
		now.set(abandoned.expiresAt());
		reset(coordinator);
		assertSuccess(send(uuid, REQUEST), 201, "CREATED");
		assertEquals(1, chats.count());
	}

	@Test
	void expiredClaimCannotSaveWithoutBeingReclaimed() {
		var claim = coordinator.acquire(uuid, REQUEST, REQUEST.requestHash());
		now.set(coordinator.observe(uuid).expiresAt());
		assertEquals(ErrorCode.AI_UNAVAILABLE, assertThrows(BusinessException.class,
			() -> writer.publish(uuid, REQUEST.requestHash(), claim.token(), REQUEST, OPENING)).getErrorCode());
		coordinator.release(uuid, claim.token());
		assertUntouchedDraft();
	}

	@Test
	void documentsRequiredHeaderStringBodyAndLimits() throws Exception {
		var result = mvc.perform(get("/v3/api-docs")).andReturn();
		var document = JSON.readTree(result.getResponse().getContentAsByteArray());
		var parameters = document.path("paths").path("/api/v1/stories").path("post").path("parameters");
		assertEquals("Idempotency-Key", parameters.get(0).path("name").stringValue());
		assertTrue(parameters.get(0).path("required").booleanValue());
		var responses = document.path("paths").path("/api/v1/stories").path("post").path("responses");
		assertTrue(responses.has("201"));
		assertTrue(responses.has("200"));
		assertTrue(responses.has("409"));
		var transcript = document.path("components").path("schemas").path("CreateStoryRequest").path("properties").path("transcript");
		assertEquals("string", transcript.path("type").stringValue());
		assertEquals(5000, transcript.path("maxLength").intValue());
	}

	private CountDownLatch observeWaiter() {
		var observed = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			observed.countDown();
			return result;
		}).when(coordinator).observe(uuid);
		return observed;
	}

	private static void await(CountDownLatch latch) throws InterruptedException {
		assertTrue(latch.await(5, TimeUnit.SECONDS), "동시 요청 동기화 시간 초과");
	}

	private Story story() {
		return stories.findById(storyId).orElseThrow();
	}

	private void assertUntouchedDraft() {
		Story stored = story();
		assertEquals(StoryStatus.DRAFT, stored.getStatus());
		assertEquals("초안 제목", stored.getTitle());
		assertEquals("초안 소개", stored.getSummary());
		assertEquals("초안 배경", stored.getBackground());
		assertEquals("초안 인물", stored.getCharacter());
		assertEquals("초안 사건", stored.getEvent());
		assertEquals(StoryCategory.ETC, stored.getCategory());
		assertNull(stored.getPrologue());
		assertNull(stored.getPublishRequestHash());
		assertNull(stored.getPublishAttemptToken());
		assertNull(stored.getPublishAttemptExpiresAt());
		assertEquals(0, chats.count());
		assertEquals(REQUEST.transcript(), records.findById(recordId).orElseThrow().getText());
	}

	private MvcResult send(String key, PublishStoryRequest request) throws Exception {
		return sendJson(key, body(request));
	}

	private MvcResult sendJson(String key, String json) throws Exception {
		return mvc.perform(post("/api/v1/stories").header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
	}

	private String body(PublishStoryRequest request) {
		return JSON.writeValueAsString(request);
	}

	private PublishStoryRequest changedOriginal() {
		return new PublishStoryRequest(REQUEST.transcript().strip(), REQUEST.title(), REQUEST.summary(),
			REQUEST.setting(), REQUEST.characters(), REQUEST.coreEvent(), REQUEST.category());
	}

	private void assertSuccess(MvcResult result, int status, String code) {
		assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsByteArray().length == 0
			? "빈 응답" : new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8));
		var response = JSON.readTree(result.getResponse().getContentAsByteArray());
		assertEquals(code, response.path("code").stringValue());
		assertEquals(1, response.path("data").size());
		assertEquals(storyId.longValue(), response.path("data").path("storyId").longValue());
	}

	private void assertError(MvcResult result, int status, String code) {
		assertEquals(status, result.getResponse().getStatus());
		var response = JSON.readTree(result.getResponse().getContentAsByteArray());
		assertEquals(code, response.path("code").stringValue());
		assertTrue(response.path("data").isNull());
		if (status == 409) {
			assertEquals("이미 다른 내용으로 사용한 등록 키입니다.", response.path("message").stringValue());
		}
	}
}
