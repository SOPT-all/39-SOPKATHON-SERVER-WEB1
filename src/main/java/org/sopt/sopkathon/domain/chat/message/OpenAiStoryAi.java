package org.sopt.sopkathon.domain.chat.message;

import java.util.List;
import java.util.Map;
import org.sopt.sopkathon.global.ai.openai.OpenAiChatClient;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class OpenAiStoryAi implements StoryAi {
	private static final Map<String, Object> TEXT = Map.of("type", "string");
	private static final Map<String, Object> OPTIONAL_TEXT = Map.of("type", List.of("string", "null"));
	private static final Map<String, Object> CHECK_FIELDS = Map.of(
		"passed", Map.of("type", "boolean"), "feedback", OPTIONAL_TEXT);
	private static final Map<String, Object> SCENE_FIELDS = Map.of(
		"scene", TEXT, "characterState", OPTIONAL_TEXT, "newEvents", Map.of("type", "array", "items", TEXT));

	private final OpenAiChatClient client;
	private final ObjectMapper mapper;
	private final String correctionInstructions;
	private final String sceneInstructions;

	OpenAiStoryAi(OpenAiChatClient client, ObjectMapper mapper,
		@Value("${app.chat.correction-instructions}") String correctionInstructions,
		@Value("${app.chat.scene-instructions}") String sceneInstructions) {
		this.client = client;
		this.mapper = mapper;
		this.correctionInstructions = correctionInstructions;
		this.sceneInstructions = sceneInstructions;
	}

	@Override
	public Check check(String text) {
		Check result = client.complete("gpt-6-luna", correctionInstructions, text, "correction", CHECK_FIELDS, Check.class,
			Map.of("max_completion_tokens", 1024, "reasoning_effort", "none"));
		if (result.passed() == null || (result.passed() ? result.feedback() != null : blank(result.feedback()))) {
			throw unavailable();
		}
		return result;
	}

	@Override
	public GeneratedScene generate(SceneInput input) {
		GeneratedScene result = client.complete("gpt-6-luna", sceneInstructions, mapper.writeValueAsString(input),
			"story_turn", SCENE_FIELDS, GeneratedScene.class, Map.of("reasoning_effort", "none"));
		if (blank(result.scene()) || (result.characterState() != null && result.characterState().isBlank())
			|| result.newEvents() == null || result.newEvents().stream().anyMatch(OpenAiStoryAi::blank)) {
			throw unavailable();
		}
		return result;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static BusinessException unavailable() {
		return new BusinessException(ErrorCode.AI_UNAVAILABLE);
	}
}
