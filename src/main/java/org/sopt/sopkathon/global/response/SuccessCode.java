package org.sopt.sopkathon.global.response;

import org.springframework.http.HttpStatus;

public enum SuccessCode {
	SUCCESS("SUCCESS", "요청에 성공했습니다.", HttpStatus.OK),
	CREATED("CREATED", "생성되었습니다.", HttpStatus.CREATED);

	private final String code;
	private final String message;
	private final HttpStatus httpStatus;

	SuccessCode(String code, String message, HttpStatus httpStatus) {
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
