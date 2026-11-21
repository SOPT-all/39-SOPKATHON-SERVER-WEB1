package org.sopt.sopkathon.domain.chat.message;

interface StoryAi {
	record Check(Boolean passed, String feedback) {}

	Check check(String text);

	GeneratedScene generate(SceneInput input);
}
