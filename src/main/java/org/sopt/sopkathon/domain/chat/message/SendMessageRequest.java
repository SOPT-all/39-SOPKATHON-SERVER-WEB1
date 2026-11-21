package org.sopt.sopkathon.domain.chat.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(@NotBlank String text, @NotNull Long lastSeenMessageId) {}
