package org.sopt.sopkathon.domain.chat.repository;

import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRepository extends JpaRepository<Chat, Long> {
}
