package org.sopt.sopkathon.domain.story.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.dto.request.CreateStoryRequest;
import org.sopt.sopkathon.domain.story.dto.response.CreateStoryResponse;
import org.sopt.sopkathon.domain.story.service.StoryPublicationService;
import org.sopt.sopkathon.global.response.ApiResponse;
import org.sopt.sopkathon.global.response.SuccessCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryPublicationController {
	private final StoryPublicationService service;

	@Operation(summary = "편집한 초안을 첫 메시지와 함께 공개 확정", description = "최초 확정 201, 동일 요청 재사용 200, 다른 본문 409")
	@ApiResponses({
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "최초 확정"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동일 요청 재사용"),
		@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "처리 중이거나 확정된 본문과 충돌")
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ApiResponse<CreateStoryResponse>> publish(
		@Parameter(required = true, description = "초안 응답에서 받은 UUID")
		@RequestHeader(value = "Idempotency-Key", required = false) String key,
		@RequestBody CreateStoryRequest request) {
		var result = service.publish(key, request.validated());
		SuccessCode success = result.created() ? SuccessCode.CREATED : SuccessCode.SUCCESS;
		return ResponseEntity.status(success.getHttpStatus())
			.body(ApiResponse.success(success, new CreateStoryResponse(result.storyId())));
	}
}
