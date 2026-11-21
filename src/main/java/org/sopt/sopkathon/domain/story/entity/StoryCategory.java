package org.sopt.sopkathon.domain.story.entity;

public enum StoryCategory {
	HORROR("공포"),
	ROMANCE("로맨스"),
	THRILLER("스릴러"),
	ACTION("액션"),
	FANTASY("판타지"),
	ETC("기타");

	private final String displayName;

	StoryCategory(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
