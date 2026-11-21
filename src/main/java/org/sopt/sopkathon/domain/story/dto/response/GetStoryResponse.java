package org.sopt.sopkathon.domain.story.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.sopkathon.domain.story.entity.Story;

@Schema(description = "공개 이야기 상세 응답")
public record GetStoryResponse(
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
	String category,

	@Schema(description = "이야기의 시간·장소·상황", example = "해 질 무렵의 소나무 숲", requiredMode = Schema.RequiredMode.REQUIRED)
	String setting,

	@Schema(
		description = "첫 문장에 주인공을 소개하고 다른 인물을 이어 쓰는 줄글",
		example = "주인공은 토끼다. 토끼는 숲에서 열쇠를 잃어버렸고, 여우는 토끼에게 길을 안내한다.",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	String characters
) {

	public static GetStoryResponse from(Story story) {
		return new GetStoryResponse(
			story.getId(),
			story.getTitle(),
			story.getSummary(),
			story.getImageUrl(),
			story.getCategory().name(),
			story.getBackground(),
			story.getCharacter()
		);
	}
}
