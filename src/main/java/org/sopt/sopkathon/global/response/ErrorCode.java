package org.sopt.sopkathon.global.response;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	INVALID_REQUEST_BODY("INVALID_REQUEST_BODY", "요청 본문의 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
	INVALID_PARAMETER("INVALID_PARAMETER", "요청 파라미터의 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
	INVALID_INPUT("INVALID_INPUT", "입력값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
	RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "요청한 주소를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
	UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type입니다.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
	INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;

	ErrorCode(String code, String message, HttpStatus httpStatus) {
		this.code = code;
		this.message = message;
		this.httpStatus = httpStatus;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
}
