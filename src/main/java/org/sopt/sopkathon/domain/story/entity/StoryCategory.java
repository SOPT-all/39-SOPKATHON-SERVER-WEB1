package org.sopt.sopkathon.domain.story.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StoryCategory {
	HORROR("공포"),
	ROMANCE("로맨스"),
	THRILLER("스릴러"),
	ACTION("액션"),
	FANTASY("판타지"),
	ETC("기타");

	private final String displayName;
}
