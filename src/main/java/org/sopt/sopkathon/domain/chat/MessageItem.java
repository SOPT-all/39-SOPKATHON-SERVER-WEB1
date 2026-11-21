package org.sopt.sopkathon.domain.chat;

import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.sopt.sopkathon.domain.chat.entity.ChatType;

public record MessageItem(long messageId, ChatType type, String text) {
	static MessageItem from(Chat chat) {
		if (chat.getType() == null || chat.getText() == null) {
			throw new IllegalStateException("저장된 채팅의 타입 또는 본문이 없습니다. messageId=" + chat.getId());
		}
		return new MessageItem(chat.getId(), chat.getType(), chat.getText());
	}
}
