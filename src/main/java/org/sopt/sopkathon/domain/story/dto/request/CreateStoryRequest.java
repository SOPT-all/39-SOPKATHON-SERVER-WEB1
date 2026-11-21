package org.sopt.sopkathon.domain.story.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import tools.jackson.databind.JsonNode;

public record CreateStoryRequest(
	@Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 5000) JsonNode transcript,
	@Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20) JsonNode title,
	@Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200) JsonNode summary,
	@Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200) JsonNode setting,
	@Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) JsonNode characters,
	@Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 500) JsonNode coreEvent,
	@Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED,
		allowableValues = {"HORROR", "ROMANCE", "THRILLER", "ACTION", "FANTASY", "ETC"}) JsonNode category
) {
	public PublishStoryRequest validated() {
		String original = text(transcript);
		String finalTitle = text(title);
		String finalSummary = text(summary);
		String finalSetting = text(setting);
		String finalCharacters = text(characters);
		String finalEvent = text(coreEvent);
		String finalCategory = text(category);
		try {
			return new PublishStoryRequest(original, finalTitle, finalSummary, finalSetting, finalCharacters,
				finalEvent, StoryCategory.valueOf(finalCategory)).validated();
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
	}

	private String text(JsonNode value) {
		if (value == null || value.isNull()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (!value.isString()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
		}
		return value.stringValue();
	}
}
