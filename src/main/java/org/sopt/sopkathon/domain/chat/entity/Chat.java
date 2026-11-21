package org.sopt.sopkathon.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.entity.Story;

@Entity
@Table(name = "chat")
@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class Chat {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "chat_id")
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "chat_type")
	private ChatType type;

	@Column(name = "chat_text", columnDefinition = "TEXT")
	private String text;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "story_id", nullable = false)
	private Story story;

	public Chat(ChatType type, String text, Story story) {
		this.type = type;
		this.text = text;
		this.story = story;
	}
}
