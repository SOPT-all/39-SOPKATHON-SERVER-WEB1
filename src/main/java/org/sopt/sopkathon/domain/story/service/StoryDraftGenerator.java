package org.sopt.sopkathon.domain.story.service;

import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryDraftResponse;

public interface StoryDraftGenerator {
	GenerateStoryDraftResponse generate(String transcript);
}
