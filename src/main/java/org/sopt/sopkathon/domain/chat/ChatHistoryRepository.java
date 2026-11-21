package org.sopt.sopkathon.domain.chat;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ChatHistoryRepository {
	private static final int PAGE_SIZE = 30;
	private final EntityManager entityManager;

	boolean existsPublishedStory(long storyId) {
		return !entityManager.createQuery("select s.id from Story s where s.id = :id and s.status = :status", Long.class)
			.setParameter("id", storyId)
			.setParameter("status", StoryStatus.PUBLISHED)
			.getResultList().isEmpty();
	}

	List<Chat> findRecentMessages(long storyId, Long beforeMessageId) {
		String before = beforeMessageId == null ? "" : " and c.id < :before";
		var query = entityManager.createQuery(
			"select c from Chat c where c.story.id = :storyId" + before + " order by c.id desc", Chat.class)
			.setParameter("storyId", storyId);
		if (beforeMessageId != null) {
			query.setParameter("before", beforeMessageId);
		}
		return query.setMaxResults(PAGE_SIZE).getResultList();
	}
}
