package org.sopt.sopkathon.global.response;

public class ApiResponse<T> {

	private final String code;
	private final String message;
	private final T data;

	private ApiResponse(String code, String message, T data) {
		this.code = code;
		this.message = message;
		this.data = data;
	}

	public static <T> ApiResponse<T> success(SuccessCode successCode, T data) {
		return new ApiResponse<>(
			successCode.getCode(),
			successCode.getMessage(),
			data
		);
	}

	public static ApiResponse<Void> success(SuccessCode successCode) {
		return success(successCode, null);
	}


	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public T getData() {
		return data;
	}


}
