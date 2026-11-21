package org.sopt.sopkathon.domain.story.service;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.dto.request.PublishStoryRequest;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryOpeningResponse;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoryPublicationService {
	private final PublicationCoordinator coordinator;
	private final StoryPublicationWriter writer;
	private final StoryOpeningGenerator generator;
	private final PublicationProperties properties;
	private final Clock clock;

	public PublicationResult publish(String key, PublishStoryRequest request) {
		String uuid = PublishStoryRequest.canonicalKey(key);
		request.validated();
		String hash = request.requestHash();
		PublicationClaim claim = coordinator.acquire(uuid, request, hash);
		if (claim.kind() == PublicationClaim.Kind.REPLAY) {
			return new PublicationResult(claim.storyId(), false);
		}
		if (claim.kind() == PublicationClaim.Kind.WAIT) {
			return await(uuid, hash, claim.token());
		}
		try {
			GenerateStoryOpeningResponse opening = generator.generate(request);
			if (opening == null) {
				throw unavailable();
			}
			return new PublicationResult(writer.publish(uuid, hash, claim.token(), request, opening.validated()), true);
		} catch (RuntimeException failure) {
			try {
				coordinator.release(uuid, claim.token());
			} catch (RuntimeException cleanupFailure) {
				if (cleanupFailure != failure) {
					failure.addSuppressed(cleanupFailure);
				}
			}
			throw failure;
		}
	}

	private PublicationResult await(String uuid, String hash, String token) {
		long started = System.nanoTime();
		long limit = properties.getWaitTimeout().toNanos();
		while (System.nanoTime() - started < limit) {
			PublicationSnapshot snapshot = coordinator.observe(uuid);
			if (snapshot.status() == StoryStatus.PUBLISHED) {
				PublicationCoordinator.checkHash(snapshot.requestHash(), hash);
				return new PublicationResult(snapshot.storyId(), false);
			}
			if (!Objects.equals(token, snapshot.token()) || snapshot.expiresAt() == null
				|| !snapshot.expiresAt().isAfter(clock.instant())) {
				throw unavailable();
			}
			PublicationCoordinator.checkHash(snapshot.requestHash(), hash);
			try {
				long remaining = limit - (System.nanoTime() - started);
				if (remaining > 0) {
					TimeUnit.NANOSECONDS.sleep(Math.min(properties.getPollInterval().toNanos(), remaining));
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw unavailable();
			}
		}
		throw unavailable();
	}

	private BusinessException unavailable() {
		return new BusinessException(ErrorCode.AI_UNAVAILABLE);
	}
}
