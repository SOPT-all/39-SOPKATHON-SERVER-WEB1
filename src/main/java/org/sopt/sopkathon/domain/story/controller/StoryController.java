package org.sopt.sopkathon.domain.story.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.dto.response.GetStoriesResponse;
import org.sopt.sopkathon.domain.story.dto.response.GetStoryResponse;
import org.sopt.sopkathon.domain.story.service.StoryService;
import org.sopt.sopkathon.global.response.ApiResponse;
import org.sopt.sopkathon.global.response.SuccessCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Story", description = "이야기 API")
@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

	private final StoryService storyService;

	@Operation(
		summary = "공개 이야기 목록 조회",
		description = "학생의 탐색 화면에 공개할 이야기를 최신 등록순으로 모두 반환합니다. "
			+ "등록된 이야기가 없으면 items는 빈 배열입니다. 인증 없이 호출합니다."
	)
	@GetMapping
	public ResponseEntity<ApiResponse<GetStoriesResponse>> getStories() {
		SuccessCode successCode = SuccessCode.SUCCESS;
		return ResponseEntity
			.status(successCode.getHttpStatus())
			.body(ApiResponse.success(successCode, storyService.getStories()));
	}

	@Operation(
		summary = "공개 이야기 상세 조회",
		description = "선택한 이야기의 소개, 배경, 등장인물을 반환합니다. 작성자의 입력 원문과 핵심 사건은 제외합니다. "
			+ "공개되지 않았거나 없는 이야기는 404 STORY_NOT_FOUND, 형식·범위가 잘못된 ID는 400 INVALID_PARAMETER입니다. "
			+ "인증 없이 호출합니다."
	)
	@GetMapping("/{storyId}")
	public ResponseEntity<ApiResponse<GetStoryResponse>> getStory(
		@Parameter(description = "등록한 이야기 ID", example = "17") @PathVariable Long storyId
	) {
		SuccessCode successCode = SuccessCode.SUCCESS;
		return ResponseEntity
			.status(successCode.getHttpStatus())
			.body(ApiResponse.success(successCode, storyService.getStory(storyId)));
	}
}
