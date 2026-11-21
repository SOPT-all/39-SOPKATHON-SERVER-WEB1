package org.sopt.sopkathon.domain.story.dto.response;

import org.sopt.sopkathon.domain.story.validation.StoryDraftText;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;

public record GenerateStoryOpeningResponse(String scene, String question) {
	public GenerateStoryOpeningResponse validated() {
		if (!StoryDraftText.fits(scene, 300) || !StoryDraftText.fits(question, 300)
			|| scene.codePoints().anyMatch(GenerateStoryOpeningResponse::questionMark)
			|| question.codePoints().filter(GenerateStoryOpeningResponse::questionMark).count() != 1
			|| !(question.stripTrailing().endsWith("?") || question.stripTrailing().endsWith("？"))) {
			throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
		}
		return this;
	}

	public String messageText() {
		return scene + "\n\n" + question;
	}

	private static boolean questionMark(int codePoint) {
		return codePoint == '?' || codePoint == '？';
	}
}
