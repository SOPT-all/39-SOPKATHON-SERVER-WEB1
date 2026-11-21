package org.sopt.sopkathon.domain.story.dto.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;

class GenerateStoryDraftResponseTest {
	@Test
	void acceptsBoundaryLengthsWithoutChangingContent() {
		var draft = new GenerateStoryDraftResponse("제".repeat(20), "요".repeat(200), "배".repeat(200),
			"인".repeat(100), StoryCategory.FANTASY, "사".repeat(500));
		assertSame(draft, draft.validated());
		assertEquals("사".repeat(500), draft.coreEvent());
	}

	@ParameterizedTest
	@CsvSource({"21,200,200,100,500", "20,201,200,100,500", "20,200,201,100,500",
		"20,200,200,101,500", "20,200,200,100,501"})
	void rejectsOverlongFields(int title, int summary, int setting, int characters, int event) {
		assertInvalid(new GenerateStoryDraftResponse("제".repeat(title), "요".repeat(summary),
			"배".repeat(setting), "인".repeat(characters), StoryCategory.FANTASY, "사".repeat(event)));
	}

	@ParameterizedTest
	@MethodSource("blankFields")
	void rejectsEveryMissingOrBlankField(int field, String value) {
		String[] fields = {"제목", "소개", "배경", "인물", "사건"};
		fields[field] = value;
		assertInvalid(new GenerateStoryDraftResponse(fields[0], fields[1], fields[2], fields[3],
			StoryCategory.FANTASY, fields[4]));
	}

	@Test
	void rejectsMissingCategory() {
		assertInvalid(new GenerateStoryDraftResponse("제목", "소개", "배경", "인물", null, "사건"));
	}

	private void assertInvalid(GenerateStoryDraftResponse draft) {
		assertEquals(ErrorCode.AI_UNAVAILABLE,
			assertThrows(BusinessException.class, draft::validated).getErrorCode());
	}

	private static Stream<Arguments> blankFields() {
		return IntStream.range(0, 5).boxed().flatMap(field ->
			Stream.of((String)null, "", " \t\n", "\u00a0").map(value -> Arguments.of(field, value)));
	}
}
