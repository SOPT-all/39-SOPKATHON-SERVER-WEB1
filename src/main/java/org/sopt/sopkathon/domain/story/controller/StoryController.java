package org.sopt.sopkathon.domain.story.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.dto.response.GetStoriesResponse;
import org.sopt.sopkathon.domain.story.service.StoryService;
import org.sopt.sopkathon.global.response.ApiResponse;
import org.sopt.sopkathon.global.response.SuccessCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
