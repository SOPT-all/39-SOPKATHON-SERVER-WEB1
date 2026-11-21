package org.sopt.sopkathon.domain.chat.message;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class MessageService {
	private final MessageStore store;
	private final StoryAi ai;
	private final Set<Long> activeStories = ConcurrentHashMap.newKeySet();

	public SendMessageResponse send(long storyId, SendMessageRequest request) {
		String text = request.text();
		if (text.codePointCount(0, text.length()) > 500) {
			throw new BusinessException(ErrorCode.INVALID_INPUT);
		}
		if (!activeStories.add(storyId)) {
			throw new BusinessException(ErrorCode.TURN_IN_PROGRESS);
		}
		try {
			store.checkLatest(storyId, request.lastSeenMessageId());
			SceneInput input = store.sceneInput(storyId, text);
			var scene = CompletableFuture.supplyAsync(() -> ai.generate(input), Thread::startVirtualThread);
			StoryAi.Check check = ai.check(text);
			if (!check.passed()) {
				return store.feedback(storyId, text, check.feedback());
			}
			return store.advance(storyId, text, scene.join());
		} catch (CompletionException ex) {
			if (ex.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw ex;
		} finally {
			activeStories.remove(storyId);
		}
	}
}
