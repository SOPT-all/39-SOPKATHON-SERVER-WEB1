package org.sopt.sopkathon.domain.chat.message;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.sopt.sopkathon.domain.chat.entity.ChatType;
import org.sopt.sopkathon.domain.context.entity.Context;
import org.sopt.sopkathon.domain.context.entity.ContextType;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
class MessageStore {
	private static final int HISTORY_TURNS = 10;
	private final EntityManager em;

	public void checkLatest(long storyId, long seen) {
		if (em.find(Story.class, storyId) == null) {
			throw new BusinessException(ErrorCode.STORY_NOT_FOUND);
		}
		Long latest = em.createQuery("select max(c.id) from Chat c where c.story.id = :id", Long.class)
			.setParameter("id", storyId).getSingleResult();
		if (!Objects.equals(latest, seen)) {
			throw new BusinessException(ErrorCode.CHAT_OUTDATED);
		}
	}

	public SceneInput sceneInput(long storyId, String text) {
		Story story = em.find(Story.class, storyId);
		List<Chat> scenes = em.createQuery("""
			select c from Chat c where c.story.id = :id and c.type = :type order by c.id desc
			""", Chat.class).setParameter("id", storyId).setParameter("type", ChatType.STORY)
			.setMaxResults(HISTORY_TURNS + 1).getResultList();
		Chat boundary = scenes.getLast();
		Long firstId = em.createQuery("select min(c.id) from Chat c where c.story.id = :id", Long.class)
			.setParameter("id", storyId).getSingleResult();
		String opening = boundary.getId().equals(firstId) ? boundary.getText() : null;
		List<Chat> messages = em.createQuery("""
			select c from Chat c where c.story.id = :id and c.id > :boundary order by c.id
			""", Chat.class).setParameter("id", storyId).setParameter("boundary", boundary.getId()).getResultList();
		List<SceneInput.Turn> turns = new ArrayList<>();
		for (int i = 1; i < messages.size(); i++) {
			Chat reply = messages.get(i);
			Chat student = messages.get(i - 1);
			if (reply.getType() == ChatType.STORY && student.getType() == ChatType.STUDENT) {
				turns.add(new SceneInput.Turn(student.getText(), reply.getText()));
			}
		}
		List<String> events = em.createQuery("""
			select c.text from Context c where c.story.id = :id and c.type = :type order by c.id
			""", String.class).setParameter("id", storyId).setParameter("type", ContextType.EVENT).getResultList();
		Context state = characterState(storyId);
		return new SceneInput(SceneInput.World.from(story), opening, turns,
			state == null ? null : state.getText(), events, text);
	}

	@Transactional
	public SendMessageResponse feedback(long storyId, String student, String feedback) {
		return savePair(em.getReference(Story.class, storyId), student, ChatType.FEEDBACK, feedback,
			SendMessageResponse.Result.NEEDS_REVISION);
	}

	@Transactional
	public SendMessageResponse advance(long storyId, String student, GeneratedScene result) {
		Story story = em.getReference(Story.class, storyId);
		SendMessageResponse response = savePair(story, student, ChatType.STORY, result.scene(),
			SendMessageResponse.Result.STORY_ADVANCED);
		if (result.characterState() != null) {
			Context state = characterState(storyId);
			if (state == null) {
				em.persist(new Context(ContextType.CHARACTER, result.characterState(), story));
			} else {
				state.updateText(result.characterState());
			}
		}
		for (String event : result.newEvents()) {
			em.persist(new Context(ContextType.EVENT, event, story));
		}
		return response;
	}

	private Context characterState(long storyId) {
		return em.createQuery("select c from Context c where c.story.id = :id and c.type = :type", Context.class)
			.setParameter("id", storyId).setParameter("type", ContextType.CHARACTER).getSingleResultOrNull();
	}

	private SendMessageResponse savePair(Story story, String text, ChatType type, String replyText,
		SendMessageResponse.Result result) {
		Chat student = new Chat(ChatType.STUDENT, text, story);
		Chat reply = new Chat(type, replyText, story);
		em.persist(student);
		em.persist(reply);
		return new SendMessageResponse(result,
			List.of(SendMessageResponse.Message.from(student), SendMessageResponse.Message.from(reply)));
	}
}
