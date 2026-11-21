package org.sopt.sopkathon.domain.story.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.dto.request.PublishStoryRequest;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;
import org.sopt.sopkathon.domain.story.repository.StoryRepository;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.sopt.sopkathon.infrastructure.openai.OpenAiProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicationCoordinator {
	private final StoryRepository stories;
	private final Clock clock;
	private final PublicationProperties properties;
	private final OpenAiProperties openAi;
	@PersistenceContext
	private EntityManager entityManager;

	@Transactional
	public PublicationClaim acquire(String uuid, PublishStoryRequest request, String hash) {
		Story story = locked(uuid);
		if (story.getStatus() == StoryStatus.PUBLISHED) {
			checkHash(story.getPublishRequestHash(), hash);
			return new PublicationClaim(PublicationClaim.Kind.REPLAY, story.getId(), null);
		}
		Instant now = clock.instant();
		if (story.hasActivePublication(now)) {
			checkHash(story.getPublishRequestHash(), hash);
			return new PublicationClaim(PublicationClaim.Kind.WAIT, story.getId(), story.getPublishAttemptToken());
		}
		if (!Objects.equals(story.getRecord().getText(), request.transcript())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		Duration minimum = openAi.getConnectTimeout().plus(openAi.getReadTimeout()).plusSeconds(30);
		Duration lease = properties.getLease().compareTo(minimum) >= 0 ? properties.getLease() : minimum;
		String token = UUID.randomUUID().toString();
		story.reservePublication(hash, token, now.plus(lease));
		return new PublicationClaim(PublicationClaim.Kind.OWNER, story.getId(), token);
	}

	@Transactional(readOnly = true)
	public PublicationSnapshot observe(String uuid) {
		return stories.publicationSnapshot(uuid).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
	}

	@Transactional
	public void release(String uuid, String token) {
		locked(uuid).releasePublication(token);
	}

	private Story locked(String uuid) {
		Story story = stories.lockByRecordUuid(uuid).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		// OpenEntityManagerInView에 남은 이전 시도의 엔티티 상태로 권한을 판단하지 않는다.
		entityManager.refresh(story);
		return story;
	}

	public static void checkHash(String existing, String requested) {
		if (!Objects.equals(existing, requested)) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
		}
	}
}
