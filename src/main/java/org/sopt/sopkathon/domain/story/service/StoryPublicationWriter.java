package org.sopt.sopkathon.domain.story.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.sopt.sopkathon.domain.chat.entity.ChatType;
import org.sopt.sopkathon.domain.chat.repository.ChatRepository;
import org.sopt.sopkathon.domain.story.dto.request.PublishStoryRequest;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryOpeningResponse;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.domain.story.repository.StoryRepository;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoryPublicationWriter {
	private final StoryRepository stories;
	private final ChatRepository chats;
	private final Clock clock;
	@PersistenceContext
	private EntityManager entityManager;

	@Transactional
	public Long publish(String uuid, String hash, String token, PublishStoryRequest request, GenerateStoryOpeningResponse opening) {
		Story story = stories.lockByRecordUuid(uuid).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		entityManager.refresh(story);
		if (!story.ownsPublication(hash, token, clock.instant())) {
			throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
		}
		story.publish(request.title(), request.summary(), request.setting(), request.characters(),
			request.coreEvent(), request.category(), opening.scene());
		chats.save(new Chat(ChatType.STORY, opening.messageText(), story));
		return story.getId();
	}
}
