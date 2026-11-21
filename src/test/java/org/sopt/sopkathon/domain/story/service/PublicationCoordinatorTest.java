package org.sopt.sopkathon.domain.story.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sopt.sopkathon.domain.record.repository.RecordRepository;
import org.sopt.sopkathon.domain.story.dto.request.PublishStoryRequest;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;
import org.sopt.sopkathon.domain.story.repository.StoryRepository;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.sopt.sopkathon.infrastructure.openai.OpenAiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PublicationCoordinatorTest {
	private static final PublishStoryRequest REQUEST = new PublishStoryRequest(
		"  최초 원문\n", "최종 제목", "소개", "배경", "주인공은 토끼다.", "열쇠를 찾는다.", StoryCategory.FANTASY);
	@Autowired private PublicationCoordinator coordinator;
	@Autowired private StoryDraftWriter drafts;
	@Autowired private StoryRepository stories;
	@Autowired private RecordRepository records;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private PublicationProperties properties;
	@Autowired private OpenAiProperties openAi;
	@Autowired private PlatformTransactionManager transactions;
	@MockitoBean private Clock clock;
	private final AtomicReference<Instant> now = new AtomicReference<>();
	private String uuid;

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
	}

	@Test
	void commitsClaimWithoutChangingDraftAndReturnsWaitForSameBody() {
		var owner = acquire(REQUEST);
		assertEquals(PublicationClaim.Kind.OWNER, owner.kind());
		var wait = acquire(REQUEST);
		assertEquals(PublicationClaim.Kind.WAIT, wait.kind());
		assertEquals(owner.token(), wait.token());
		var stored = stories.findById(owner.storyId()).orElseThrow();
		assertEquals(StoryStatus.DRAFT, stored.getStatus());
		assertEquals("초안 제목", stored.getTitle());
		assertEquals(REQUEST.requestHash(), stored.getPublishRequestHash());
		assertTrue(stored.getPublishAttemptExpiresAt().isAfter(now.get()));
	}

	@Test
	void activeDifferentBodyConflictsBeforeTranscriptComparison() {
		acquire(REQUEST);
		assertCode(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, () -> acquire(changedOriginal()));
	}

	@Test
	void draftRequiresExactOriginalAndUnknownKeyReturns404() {
		assertCode(ErrorCode.INVALID_INPUT, () -> acquire(changedOriginal()));
		assertNull(coordinator.observe(uuid).token());
		assertCode(ErrorCode.RESOURCE_NOT_FOUND,
			() -> coordinator.acquire(UUID.randomUUID().toString(), REQUEST, REQUEST.requestHash()));
	}

	@Test
	void releaseOnlyClearsItsOwnTokenAndAllowsEditedRetry() {
		var owner = acquire(REQUEST);
		coordinator.release(uuid, UUID.randomUUID().toString());
		assertEquals(owner.token(), coordinator.observe(uuid).token());
		coordinator.release(uuid, owner.token());
		assertNull(coordinator.observe(uuid).requestHash());
		assertNull(coordinator.observe(uuid).token());
		assertEquals(PublicationClaim.Kind.OWNER, acquire(changedTitle()).kind());
	}

	@Test
	void expirationReclaimsWithNewTokenAndStaleReleaseCannotClearNewOwner() {
		var old = acquire(REQUEST);
		now.set(coordinator.observe(uuid).expiresAt());
		var fresh = acquire(changedTitle());
		assertEquals(PublicationClaim.Kind.OWNER, fresh.kind());
		assertNotEquals(old.token(), fresh.token());
		coordinator.release(uuid, old.token());
		assertEquals(fresh.token(), coordinator.observe(uuid).token());
	}

	@Test
	void publishedSameBodyReplaysAndOtherBodyConflicts() {
		var owner = acquire(REQUEST);
		new TransactionTemplate(transactions).executeWithoutResult(status -> {
			var story = stories.lockByRecordUuid(uuid).orElseThrow();
			story.publish(REQUEST.title(), REQUEST.summary(), REQUEST.setting(), REQUEST.characters(),
				REQUEST.coreEvent(), REQUEST.category(), "첫 장면");
		});
		var replay = acquire(REQUEST);
		assertEquals(PublicationClaim.Kind.REPLAY, replay.kind());
		assertEquals(owner.storyId(), replay.storyId());
		coordinator.release(uuid, owner.token());
		assertEquals(REQUEST.requestHash(), coordinator.observe(uuid).requestHash());
		assertCode(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, () -> acquire(changedOriginal()));
	}

	@Test
	void leaseExceedsProviderTimeoutEvenIfConfiguredShorter() {
		Duration original = properties.getLease();
		try {
			properties.setLease(Duration.ofMillis(1));
			acquire(REQUEST);
			assertEquals(now.get().plus(openAi.getConnectTimeout()).plus(openAi.getReadTimeout()).plusSeconds(30),
				coordinator.observe(uuid).expiresAt());
		} finally {
			properties.setLease(original);
		}
	}

	@Test
	void rejectsNonPositivePublicationSettings() {
		PublicationProperties invalid = new PublicationProperties();
		invalid.setWaitTimeout(Duration.ZERO);
		assertThrows(IllegalArgumentException.class, () -> new PublicationConfig().publicationClock(invalid));
	}

	@Test
	void legacyPublishedStoryWithoutHashIsNotRepublished() {
		jdbc.update("update story set story_status = 'PUBLISHED'");
		assertCode(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, () -> acquire(REQUEST));
		assertNull(coordinator.observe(uuid).token());
	}

	private PublicationClaim acquire(PublishStoryRequest request) {
		return coordinator.acquire(uuid, request, request.requestHash());
	}

	private PublishStoryRequest changedOriginal() {
		return new PublishStoryRequest(REQUEST.transcript().strip(), REQUEST.title(), REQUEST.summary(),
			REQUEST.setting(), REQUEST.characters(), REQUEST.coreEvent(), REQUEST.category());
	}

	private PublishStoryRequest changedTitle() {
		return new PublishStoryRequest(REQUEST.transcript(), "다른 제목", REQUEST.summary(),
			REQUEST.setting(), REQUEST.characters(), REQUEST.coreEvent(), REQUEST.category());
	}

	private void assertCode(ErrorCode code, Runnable action) {
		assertEquals(code, assertThrows(BusinessException.class, action::run).getErrorCode());
	}
}
