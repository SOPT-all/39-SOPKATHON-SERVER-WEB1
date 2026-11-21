package org.sopt.sopkathon.domain.chat;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ApiResponse;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.sopt.sopkathon.global.response.SuccessCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ChatHistoryController {
	private static final Pattern DECIMAL_ID = Pattern.compile("[0-9]+");
	private final ChatHistoryService service;

	@GetMapping("/api/v1/stories/{storyId}/messages")
	public ApiResponse<GetChatHistoryResponse> getChatHistory(
		@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
		@PathVariable String storyId,
		@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1"))
		@RequestParam(required = false) String beforeMessageId) {
		long id = parsePositiveId(storyId);
		Long cursor = beforeMessageId == null ? null : parsePositiveId(beforeMessageId);
		return ApiResponse.success(SuccessCode.SUCCESS, service.getChatHistory(id, cursor));
	}

	private long parsePositiveId(String value) {
		if (!DECIMAL_ID.matcher(value).matches()) {
			throw new BusinessException(ErrorCode.INVALID_PARAMETER);
		}
		long id;
		try {
			id = Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new BusinessException(ErrorCode.INVALID_PARAMETER);
		}
		if (id == 0) {
			throw new BusinessException(ErrorCode.INVALID_PARAMETER);
		}
		return id;
	}
}
