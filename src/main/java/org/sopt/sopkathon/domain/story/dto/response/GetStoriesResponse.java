package org.sopt.sopkathon.domain.story.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.sopkathon.domain.story.entity.Story;

@Schema(description = "공개 이야기 목록 응답")
public record GetStoriesResponse(
	@Schema(description = "화면에 표시할 목록", requiredMode = Schema.RequiredMode.REQUIRED)
	List<StoryItem> items
) {

	public static GetStoriesResponse from(List<Story> stories) {
		return new GetStoriesResponse(
			stories.stream().map(StoryItem::from).toList()
		);
	}

	@Schema(description = "공개 이야기 항목")
	public record StoryItem(
		@Schema(description = "등록한 이야기 ID", example = "17", requiredMode = Schema.RequiredMode.REQUIRED)
		Long storyId,

		@Schema(description = "이야기 제목", example = "토끼와 여우의 열쇠 찾기", requiredMode = Schema.RequiredMode.REQUIRED)
		String title,

		@Schema(
			description = "핵심 사건의 결과를 숨긴 이야기 소개문",
			example = "숲에서 열쇠를 잃어버린 토끼가 여우를 만나요.",
			requiredMode = Schema.RequiredMode.REQUIRED
		)
		String summary,

		@Schema(description = "이야기 이미지 URL", example = "https://example.com/images/story-17.jpg", nullable = true)
		String imageUrl,

		@Schema(
			description = "카테고리 영문 대문자 코드",
			example = "FANTASY",
			allowableValues = {"HORROR", "ROMANCE", "THRILLER", "ACTION", "FANTASY", "ETC"},
			requiredMode = Schema.RequiredMode.REQUIRED
		)
		String category
	) {

		public static StoryItem from(Story story) {
			return new StoryItem(
				story.getId(),
				story.getTitle(),
				story.getSummary(),
				story.getImageUrl(),
				story.getCategory().name()
			);
		}
	}
}
