package org.sopt.sopkathon.domain.story.service;

import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.dto.response.GetStoriesResponse;
import org.sopt.sopkathon.domain.story.repository.StoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoryService {

	private final StoryRepository storyRepository;

	public GetStoriesResponse getStories() {
		return GetStoriesResponse.from(storyRepository.findAllByOrderByIdDesc());
	}
}
