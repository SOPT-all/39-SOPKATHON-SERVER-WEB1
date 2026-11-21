package org.sopt.sopkathon.domain.story.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.sopt.sopkathon.domain.chat.entity.ChatType;
import org.sopt.sopkathon.domain.context.entity.Context;
import org.sopt.sopkathon.domain.context.entity.ContextType;
import org.sopt.sopkathon.domain.record.entity.Record;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.test.database.replace=none")
@ActiveProfiles("test")
class StoryContentTest {

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savesMultipleChatsAndContextsForSameStory() {
		Story story = persistStory();
		String longText = "긴 내용".repeat(1000);
		Chat firstChat = new Chat(ChatType.STUDENT, longText, story);
		Chat secondChat = new Chat(ChatType.STORY, "이어지는 장면", story);
		Context firstContext = new Context(ContextType.CHARACTER, longText, story);
		Context secondContext = new Context(ContextType.EVENT, "추가 사건", story);
		entityManager.persist(firstChat);
		entityManager.persist(secondChat);
		entityManager.persist(firstContext);
		entityManager.persist(secondContext);
		entityManager.flush();
		entityManager.clear();

		assertEquals(longText, entityManager.find(Chat.class, firstChat.getId()).getText());
		assertEquals(longText, entityManager.find(Context.class, firstContext.getId()).getText());
		for (Long id : new Long[] {firstChat.getId(), secondChat.getId()}) {
			assertEquals(story.getId(), entityManager.find(Chat.class, id).getStory().getId());
		}
		for (Long id : new Long[] {firstContext.getId(), secondContext.getId()}) {
			assertEquals(story.getId(), entityManager.find(Context.class, id).getStory().getId());
		}
	}

	@Test
	void allowsNullableChatAndContextAttributes() {
		Story story = persistStory();
		Chat chat = new Chat(null, null, story);
		Context context = new Context(null, null, story);
		entityManager.persist(chat);
		entityManager.persist(context);
		entityManager.flush();
		entityManager.clear();

		Chat foundChat = entityManager.find(Chat.class, chat.getId());
		Context foundContext = entityManager.find(Context.class, context.getId());
		assertNull(foundChat.getType());
		assertNull(foundChat.getText());
		assertNull(foundContext.getType());
		assertNull(foundContext.getText());
	}

	@ParameterizedTest
	@EnumSource(ChatType.class)
	void storesChatTypeAsString(ChatType type) {
		Chat chat = new Chat(type, "채팅", persistStory());
		entityManager.persist(chat);
		entityManager.flush();
		entityManager.clear();
		assertEquals(type, entityManager.find(Chat.class, chat.getId()).getType());
		assertEquals(type.name(), entityManager.createNativeQuery(
			"select chat_type from chat where chat_id = :id", String.class)
			.setParameter("id", chat.getId()).getSingleResult());
	}

	@ParameterizedTest
	@EnumSource(ContextType.class)
	void storesContextTypeAsString(ContextType type) {
		Context context = new Context(type, "맥락", persistStory());
		entityManager.persist(context);
		entityManager.flush();
		entityManager.clear();
		assertEquals(type, entityManager.find(Context.class, context.getId()).getType());
		assertEquals(type.name(), entityManager.createNativeQuery(
			"select context_type from context where context_id = :id", String.class)
			.setParameter("id", context.getId()).getSingleResult());
	}

	@Test
	void rejectsChatWithoutStory() {
		assertThrows(PersistenceException.class, () -> {
			entityManager.persist(new Chat(ChatType.STUDENT, "채팅", null));
			entityManager.flush();
		});
	}

	@Test
	void rejectsContextWithoutStory() {
		assertThrows(PersistenceException.class, () -> {
			entityManager.persist(new Context(ContextType.EVENT, "맥락", null));
			entityManager.flush();
		});
	}

	@Test
	void rejectsChatWithNonexistentStory() {
		Story missing = entityManager.getReference(Story.class, Long.MAX_VALUE);
		assertThrows(ConstraintViolationException.class, () -> {
			entityManager.persist(new Chat(ChatType.STUDENT, "채팅", missing));
			entityManager.flush();
		});
	}

	@Test
	void rejectsContextWithNonexistentStory() {
		Story missing = entityManager.getReference(Story.class, Long.MAX_VALUE);
		assertThrows(ConstraintViolationException.class, () -> {
			entityManager.persist(new Context(ContextType.EVENT, "맥락", missing));
			entityManager.flush();
		});
	}

	private Story persistStory() {
		Record record = new Record("녹음 원문", "record-uuid");
		entityManager.persist(record);
		Story story = new Story("세계관", StoryCategory.FANTASY, null, null, null, null, null, null, record);
		entityManager.persist(story);
		return story;
	}
}
