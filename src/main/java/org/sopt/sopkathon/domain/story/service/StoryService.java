package org.sopt.sopkathon.domain.story.service;

import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.dto.response.GetStoriesResponse;
import org.sopt.sopkathon.domain.story.dto.response.GetStoryResponse;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;
import org.sopt.sopkathon.domain.story.repository.StoryRepository;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoryService {

	private final StoryRepository storyRepository;

	public GetStoriesResponse getStories() {
		return GetStoriesResponse.from(storyRepository.findAllByStatusOrderByIdDesc(StoryStatus.PUBLISHED));
	}

	public GetStoryResponse getStory(Long storyId) {
		if (storyId <= 0) {
			throw new BusinessException(ErrorCode.INVALID_PARAMETER);
		}
		return storyRepository.findByIdAndStatus(storyId, StoryStatus.PUBLISHED)
			.map(GetStoryResponse::from)
			.orElseThrow(() -> new BusinessException(ErrorCode.STORY_NOT_FOUND));
	}
}
