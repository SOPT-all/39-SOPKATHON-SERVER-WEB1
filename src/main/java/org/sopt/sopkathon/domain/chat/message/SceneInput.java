package org.sopt.sopkathon.domain.chat.message;

import java.util.List;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;

record SceneInput(World world, String openingScene, List<Turn> recentTurns, String characterState,
	List<String> events, String studentText) {

	record World(String title, StoryCategory category, String background, String summary,
		String characters, String keyEvents) {
		static World from(Story story) {
			return new World(story.getTitle(), story.getCategory(), story.getBackground(),
				story.getSummary(), story.getCharacter(), story.getEvent());
		}
	}

	record Turn(String studentText, String scene) {}
}
