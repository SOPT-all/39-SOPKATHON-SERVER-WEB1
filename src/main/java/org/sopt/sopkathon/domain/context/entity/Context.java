package org.sopt.sopkathon.domain.context.entity;

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
import org.sopt.sopkathon.domain.story.entity.Story;

@Entity
@Table(name = "context")
public class Context {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "context_id")
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "context_type")
	private ContextType type;

	@Column(name = "context_text", columnDefinition = "TEXT")
	private String text;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "story_id", nullable = false)
	private Story story;

	protected Context() {
	}

	public Context(ContextType type, String text, Story story) {
		this.type = type;
		this.text = text;
		this.story = story;
	}

	public Long getId() {
		return id;
	}

	public ContextType getType() {
		return type;
	}

	public String getText() {
		return text;
	}

	public Story getStory() {
		return story;
	}
}
