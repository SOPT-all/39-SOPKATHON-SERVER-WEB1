package org.sopt.sopkathon.domain.chat;

import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ChatHistoryService {
	private final ChatHistoryRepository repository;

	@Transactional(readOnly = true)
	public GetChatHistoryResponse getChatHistory(long storyId, Long beforeMessageId) {
		if (!repository.existsPublishedStory(storyId)) {
			throw new BusinessException(ErrorCode.STORY_NOT_FOUND);
		}
		var messages = repository.findRecentMessages(storyId, beforeMessageId);
		return new GetChatHistoryResponse(messages.reversed().stream().map(MessageItem::from).toList());
	}
}
