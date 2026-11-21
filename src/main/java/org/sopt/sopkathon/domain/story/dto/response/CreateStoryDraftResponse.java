package org.sopt.sopkathon.domain.story.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;

public record CreateStoryDraftResponse(
	@Schema(format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
	String recordUuid,
	@Schema(maxLength = 20, requiredMode = Schema.RequiredMode.REQUIRED)
	String title,
	@Schema(maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED, description = "결말을 숨긴 소개문")
	String summary,
	@Schema(maxLength = 200, requiredMode = Schema.RequiredMode.REQUIRED)
	String setting,
	@Schema(maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
	String characters,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
	StoryCategory category,
	@Schema(maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
	String coreEvent
) {
	public static CreateStoryDraftResponse from(Story story) {
		return new CreateStoryDraftResponse(story.getRecord().getUuid(), story.getTitle(), story.getSummary(),
			story.getBackground(), story.getCharacter(), story.getCategory(), story.getEvent());
	}
}
