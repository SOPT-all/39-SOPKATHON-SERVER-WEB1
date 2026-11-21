package org.sopt.sopkathon.domain.story.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import tools.jackson.databind.JsonNode;

public record CreateStoryDraftRequest(
	@Schema(type = "string", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 5000,
		description = "브라우저가 확정한 최초 원문. 공백만인 값은 불가하며 유니코드 코드 포인트 최대 5,000자.",
		example = "토끼가 숲에서 열쇠를 잃어버렸어요. 여우와 함께 찾았어요.")
	JsonNode transcript
) {
	public String transcriptValue() {
		if (transcript == null || transcript.isNull()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (!transcript.isString()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
		}
		return transcript.stringValue();
	}
}
