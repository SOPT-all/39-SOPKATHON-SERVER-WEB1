package org.sopt.sopkathon.domain.chat;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.global.response.ApiResponse;
import org.sopt.sopkathon.global.response.SuccessCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ChatHistoryController {
	private final ChatHistoryService service;

	@GetMapping("/api/v1/stories/{storyId}/messages")
	public ApiResponse<GetChatHistoryResponse> getChatHistory(
		@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
		@PathVariable String storyId,
		@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
		@RequestParam(required = false) String beforeMessageId) {
		long id = PositiveId.parse(storyId);
		Long cursor = beforeMessageId == null ? null : PositiveId.parse(beforeMessageId);
		return ApiResponse.success(SuccessCode.SUCCESS, service.getChatHistory(id, cursor));
	}
}
