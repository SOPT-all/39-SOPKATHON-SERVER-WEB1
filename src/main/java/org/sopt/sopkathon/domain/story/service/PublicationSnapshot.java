package org.sopt.sopkathon.domain.story.service;

import java.time.Instant;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;

public record PublicationSnapshot(Long storyId, StoryStatus status, String requestHash, String token, Instant expiresAt) {
}
