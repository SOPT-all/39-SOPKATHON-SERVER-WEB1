package org.sopt.sopkathon.domain.chat.message;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.chat.PositiveId;
import org.sopt.sopkathon.global.response.ApiResponse;
import org.sopt.sopkathon.global.response.SuccessCode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
class MessageController {
	private final MessageService service;

	@PostMapping("/api/v1/stories/{storyId}/messages")
	public ApiResponse<SendMessageResponse> send(
		@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
		@PathVariable String storyId, @Valid @RequestBody SendMessageRequest request) {
		return ApiResponse.success(SuccessCode.SUCCESS, service.send(PositiveId.parse(storyId), request));
	}
}
