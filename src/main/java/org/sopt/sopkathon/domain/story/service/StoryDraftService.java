package org.sopt.sopkathon.domain.story.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.story.dto.response.CreateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.validation.StoryDraftText;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoryDraftService {
	private final StoryDraftGenerator generator;
	private final StoryDraftWriter writer;

	public CreateStoryDraftResponse create(String transcript) {
		if (!StoryDraftText.fits(transcript, 5000)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		String uuid = UUID.randomUUID().toString();
		GenerateStoryDraftResponse draft = generator.generate(transcript);
		if (draft == null) {
			throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
		}
		// AI 호출과 검증을 끝낸 뒤 별도 빈에서 짧은 저장 트랜잭션을 시작한다.
		return writer.save(uuid, transcript, draft.validated());
	}
}
