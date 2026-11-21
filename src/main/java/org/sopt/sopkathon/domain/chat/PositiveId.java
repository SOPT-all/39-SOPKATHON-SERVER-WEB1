package org.sopt.sopkathon.domain.chat;

import java.util.regex.Pattern;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;

public final class PositiveId {
	private static final Pattern DECIMAL_ID = Pattern.compile("[0-9]+");

	private PositiveId() {}

	public static long parse(String value) {
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
