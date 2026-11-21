package org.sopt.sopkathon.domain.story.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.sopt.sopkathon.domain.record.entity.Record;

@Entity
@Table(name = "story")
public class Story {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "story_id")
	private Long id;

	@Column(name = "story_title", length = 20)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "story_catagory")
	private StoryCategory category;

	@Column(name = "story_background", length = 200)
	private String background;

	@Column(name = "story_character", length = 100)
	private String character;

	@Column(name = "story_event", length = 500)
	private String event;

	@Column(name = "story_prologue", length = 300)
	private String prologue;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "record_id", nullable = false, unique = true)
	private Record record;

	protected Story() {
	}

	public Story(String title, StoryCategory category, String background, String character,
		String event, String prologue, Record record) {
		this.title = title;
		this.category = category;
		this.background = background;
		this.character = character;
		this.event = event;
		this.prologue = prologue;
		this.record = record;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public StoryCategory getCategory() {
		return category;
	}

	public String getBackground() {
		return background;
	}

	public String getCharacter() {
		return character;
	}

	public String getEvent() {
		return event;
	}

	public String getPrologue() {
		return prologue;
	}

	public Record getRecord() {
		return record;
	}
}
