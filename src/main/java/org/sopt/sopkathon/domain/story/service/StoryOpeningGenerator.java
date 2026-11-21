package org.sopt.sopkathon.domain.story.service;

import org.sopt.sopkathon.domain.story.dto.request.PublishStoryRequest;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryOpeningResponse;

public interface StoryOpeningGenerator {
	GenerateStoryOpeningResponse generate(PublishStoryRequest request);
}
