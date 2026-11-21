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
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.record.entity.Record;

@Entity
@Table(name = "story")
@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class Story {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "story_id")
	private Long id;

	@Column(name = "story_title", length = 20)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(name = "story_category")
	private StoryCategory category;

	@Column(name = "story_background", length = 200)
	private String background;

	@Column(name = "story_summary", length = 200)
	private String summary;

	@Column(name = "story_character", length = 100)
	private String character;

	@Column(name = "story_event", length = 500)
	private String event;

	@Column(name = "story_prologue", length = 300)
	private String prologue;

	@Column(name = "image_url", length = 300)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "story_status", nullable = false, length = 20)
	private StoryStatus status = StoryStatus.DRAFT;

	@Column(name = "publish_request_hash", length = 64)
	private String publishRequestHash;

	@Column(name = "publish_attempt_token", length = 36)
	private String publishAttemptToken;

	@Column(name = "publish_attempt_expires_at")
	private Instant publishAttemptExpiresAt;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "record_id", nullable = false, unique = true)
	private Record record;

	public Story(String title, StoryCategory category, String background, String summary,
		String character, String event, String prologue, String imageUrl, Record record) {
		this.title = title;
		this.category = category;
		this.background = background;
		this.summary = summary;
		this.character = character;
		this.event = event;
		this.prologue = prologue;
		this.imageUrl = imageUrl;
		this.record = record;
	}

	public static Story draft(String title, StoryCategory category, String background, String summary,
		String character, String event, Record record) {
		return new Story(title, category, background, summary, character, event, null, null, record);
	}

	public boolean hasActivePublication(Instant now) {
		return publishAttemptToken != null && publishAttemptExpiresAt != null && publishAttemptExpiresAt.isAfter(now);
	}

	public boolean ownsPublication(String hash, String token, Instant now) {
		return status == StoryStatus.DRAFT && Objects.equals(publishRequestHash, hash)
			&& Objects.equals(publishAttemptToken, token) && hasActivePublication(now);
	}

	public void reservePublication(String hash, String token, Instant expiresAt) {
		publishRequestHash = hash;
		publishAttemptToken = token;
		publishAttemptExpiresAt = expiresAt;
	}

	public void releasePublication(String token) {
		if (status == StoryStatus.DRAFT && Objects.equals(publishAttemptToken, token)) {
			publishRequestHash = null;
			publishAttemptToken = null;
			publishAttemptExpiresAt = null;
		}
	}

	public void publish(String title, String summary, String background, String character,
		String event, StoryCategory category, String scene) {
		this.title = title;
		this.summary = summary;
		this.background = background;
		this.character = character;
		this.event = event;
		this.category = category;
		this.prologue = scene;
		this.status = StoryStatus.PUBLISHED;
		this.publishAttemptToken = null;
		this.publishAttemptExpiresAt = null;
	}
}
