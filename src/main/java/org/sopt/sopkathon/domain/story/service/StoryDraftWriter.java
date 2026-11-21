package org.sopt.sopkathon.domain.story.service;

import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.record.entity.Record;
import org.sopt.sopkathon.domain.record.repository.RecordRepository;
import org.sopt.sopkathon.domain.story.dto.response.CreateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.dto.response.GenerateStoryDraftResponse;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.domain.story.repository.StoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoryDraftWriter {
	private final RecordRepository recordRepository;
	private final StoryRepository storyRepository;

	@Transactional
	public CreateStoryDraftResponse save(String recordUuid, String transcript, GenerateStoryDraftResponse draft) {
		Record record = recordRepository.save(new Record(transcript, recordUuid));
		Story story = storyRepository.save(Story.draft(draft.title(), draft.category(), draft.setting(),
			draft.summary(), draft.characters(), draft.coreEvent(), record));
		return CreateStoryDraftResponse.from(story);
	}
}
