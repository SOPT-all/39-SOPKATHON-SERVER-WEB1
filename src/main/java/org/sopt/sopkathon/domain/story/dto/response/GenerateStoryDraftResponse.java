package org.sopt.sopkathon.domain.story.dto.response;

import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.domain.story.validation.StoryDraftText;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;

public record GenerateStoryDraftResponse(
	String title,
	String summary,
	String setting,
	String characters,
	StoryCategory category,
	String coreEvent
) {
	public GenerateStoryDraftResponse validated() {
		if (!StoryDraftText.fits(title, 20) || !StoryDraftText.fits(summary, 200)
			|| !StoryDraftText.fits(setting, 200) || !StoryDraftText.fits(characters, 100)
			|| category == null || !StoryDraftText.fits(coreEvent, 500)) {
			throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
		}
		return this;
	}
}
