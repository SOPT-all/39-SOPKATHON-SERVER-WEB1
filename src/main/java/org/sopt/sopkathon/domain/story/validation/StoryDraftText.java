package org.sopt.sopkathon.domain.story.validation;

public final class StoryDraftText {
	private StoryDraftText() {
	}

	public static boolean hasText(String value) {
		return value != null && value.codePoints()
			.anyMatch(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint));
	}

	public static boolean fits(String value, int maximum) {
		return hasText(value) && value.codePointCount(0, value.length()) <= maximum;
	}
}
