package org.sopt.sopkathon.domain.story.dto.request;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.domain.story.validation.StoryDraftText;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import tools.jackson.databind.json.JsonMapper;

public record PublishStoryRequest(String transcript, String title, String summary, String setting,
	String characters, String coreEvent, StoryCategory category) {
	private static final JsonMapper HASH_JSON = JsonMapper.builder().build();

	public PublishStoryRequest validated() {
		if (!StoryDraftText.fits(transcript, 5000) || !StoryDraftText.fits(title, 20)
			|| !StoryDraftText.fits(summary, 200) || !StoryDraftText.fits(setting, 200)
			|| !StoryDraftText.fits(characters, 100) || !StoryDraftText.fits(coreEvent, 500) || category == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		return this;
	}

	public String requestHash() {
		try {
			byte[] canonical = HASH_JSON.writeValueAsBytes(List.of(transcript, title, summary, setting,
				characters, coreEvent, category.name()));
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
		}
	}

	public static String canonicalKey(String key) {
		if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
			throw new BusinessException(ErrorCode.INVALID_PARAMETER);
		}
		return UUID.fromString(key).toString();
	}
}
