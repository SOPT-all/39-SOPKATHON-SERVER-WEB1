package org.sopt.sopkathon.domain.chat.message;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.global.response.ApiResponse;
import org.sopt.sopkathon.global.response.SuccessCode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
class MessageController {
	private final MessageService service;

	@PostMapping("/api/v1/stories/{storyId}/messages")
	public ApiResponse<SendMessageResponse> send(@PathVariable long storyId, @Valid @RequestBody SendMessageRequest request) {
		return ApiResponse.success(SuccessCode.SUCCESS, service.send(storyId, request));
	}
}
