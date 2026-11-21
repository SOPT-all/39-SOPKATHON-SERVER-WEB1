package org.sopt.sopkathon.domain.story.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.dto.request.CreateStoryDraftRequest;
import org.sopt.sopkathon.domain.story.dto.response.CreateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.service.StoryDraftService;
import org.sopt.sopkathon.global.response.ApiResponse;
import org.sopt.sopkathon.global.response.SuccessCode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "이야기 초안")
@RestController
@RequestMapping("/api/v1/story-drafts")
@RequiredArgsConstructor
public class StoryDraftController {
	private final StoryDraftService service;

	@Operation(summary = "음성 인식 원문으로 편집용 이야기 초안 생성",
		description = "인증 없이 JSON 원문을 받아 미공개 초안을 저장합니다. 원문은 코드 포인트 최대 5,000자입니다.")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "편집용 초안"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 JSON·타입 또는 유효하지 않은 원문"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "JSON이 아닌 요청"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "AI를 사용할 수 없음")
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<CreateStoryDraftResponse> create(@RequestBody CreateStoryDraftRequest request) {
		return ApiResponse.success(SuccessCode.SUCCESS, service.create(request.transcriptValue()));
	}
}
