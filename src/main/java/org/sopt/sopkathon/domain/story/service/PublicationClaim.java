package org.sopt.sopkathon.domain.story.service;

public record PublicationClaim(Kind kind, Long storyId, String token) {
	public enum Kind { OWNER, WAIT, REPLAY }
}
