package org.sopt.sopkathon.domain.chat;

import java.util.List;

public record GetChatHistoryResponse(List<MessageItem> messages) {}
