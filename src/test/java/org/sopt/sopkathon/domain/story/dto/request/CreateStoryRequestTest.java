package org.sopt.sopkathon.domain.story.dto.request;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import tools.jackson.databind.json.JsonMapper;

class CreateStoryRequestTest {
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void hashIgnoresJsonOrderButIncludesEveryFinalFieldAndOriginal() {
		Map<String, Object> source = body();
		PublishStoryRequest original = request(source);
		Map<String, Object> reverse = new LinkedHashMap<>();
		source.keySet().stream().toList().reversed().forEach(key -> reverse.put(key, source.get(key)));
		assertEquals(original.requestHash(), request(reverse).requestHash());
		assertEquals(64, original.requestHash().length());
		for (String field : source.keySet()) {
			Map<String, Object> changed = body();
			changed.put(field, field.equals("category") ? "ROMANCE" : "다른 값");
			assertNotEquals(original.requestHash(), request(changed).requestHash());
		}
	}

	@Test
	void preservesWhitespaceAndAcceptsUnicodeBoundary() {
		Map<String, Object> values = body();
		String raw = "🐰".repeat(5000);
		values.put("transcript", raw);
		values.put("title", " 제목 ");
		assertEquals(raw, request(values).transcript());
		assertEquals(" 제목 ", request(values).title());
		values.put("transcript", raw + "a");
		assertError(values, ErrorCode.INVALID_INPUT);
	}

	@ParameterizedTest
	@ValueSource(strings = {"transcript", "title", "summary", "setting", "characters", "coreEvent", "category"})
	void rejectsNonStringAndMissingFields(String field) {
		Map<String, Object> values = body();
		values.put(field, 123);
		assertError(values, ErrorCode.INVALID_REQUEST_BODY);
		values.put(field, null);
		assertError(values, ErrorCode.INVALID_INPUT);
		values.remove(field);
		assertError(values, ErrorCode.INVALID_INPUT);
		values.put(field, "\u00a0\n");
		assertError(values, ErrorCode.INVALID_INPUT);
	}

	@ParameterizedTest
	@MethodSource("lengths")
	void validatesEachFieldLength(String field, int maximum) {
		Map<String, Object> values = body();
		values.put(field, "가".repeat(maximum));
		assertNotNull(request(values));
		values.put(field, "가".repeat(maximum + 1));
		assertError(values, ErrorCode.INVALID_INPUT);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"invalid", "1-1-1-1-1", " 550e8400-e29b-41d4-a716-446655440000", "550e8400e29b41d4a716446655440000"})
	void rejectsNonCanonicalUuid(String key) {
		assertEquals(ErrorCode.INVALID_PARAMETER,
			assertThrows(BusinessException.class, () -> PublishStoryRequest.canonicalKey(key)).getErrorCode());
	}

	@Test
	void canonicalizesUppercaseUuidAndRejectsCategory() {
		String uuid = UUID.randomUUID().toString();
		assertEquals(uuid, PublishStoryRequest.canonicalKey(uuid.toUpperCase()));
		Map<String, Object> values = body();
		values.put("category", "fantasy");
		assertError(values, ErrorCode.INVALID_INPUT);
	}

	private static Map<String, Object> body() {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("transcript", "최초 원문"); values.put("title", "최종 제목"); values.put("summary", "소개문");
		values.put("setting", "숲"); values.put("characters", "주인공은 토끼다.");
		values.put("coreEvent", "열쇠 찾기"); values.put("category", "FANTASY");
		return values;
	}

	private static PublishStoryRequest request(Map<String, Object> values) {
		return JSON.readValue(JSON.writeValueAsString(values), CreateStoryRequest.class).validated();
	}

	private void assertError(Map<String, Object> values, ErrorCode code) {
		assertEquals(code, assertThrows(BusinessException.class, () -> request(values)).getErrorCode());
	}

	private static Stream<Arguments> lengths() {
		return Stream.of(Arguments.of("title", 20), Arguments.of("summary", 200), Arguments.of("setting", 200),
			Arguments.of("characters", 100), Arguments.of("coreEvent", 500));
	}
}
