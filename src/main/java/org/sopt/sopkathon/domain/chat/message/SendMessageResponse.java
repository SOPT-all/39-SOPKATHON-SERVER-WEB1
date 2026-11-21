package org.sopt.sopkathon.domain.chat.message;

import java.util.List;
import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.sopt.sopkathon.domain.chat.entity.ChatType;

public record SendMessageResponse(Result result, List<Message> messages) {
	public enum Result { NEEDS_REVISION, STORY_ADVANCED }

	public record Message(Long messageId, ChatType type, String text) {
		static Message from(Chat chat) {
			return new Message(chat.getId(), chat.getType(), chat.getText());
		}
	}
}
