package org.sopt.sopkathon.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.sopt.sopkathon.domain.chat.entity.ChatType;
import org.sopt.sopkathon.domain.record.entity.Record;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatHistoryControllerTest {
	private static final String URL = "/api/v1/stories/{storyId}/messages";

	@Autowired MockMvc mvc;
	@Autowired EntityManager em;
	@Autowired JsonMapper json;
	@Autowired ChatHistoryRepository repository;

	@Test
	void returnsTheSavedOpeningAndBothKindsOfTurnInSendOrder() throws Exception {
		Story story = story(true);
		Chat opening = save(story, ChatType.STORY, "첫 장면. 어디로 갈까?");
		expectPage(story, null, List.of(opening));
		expectPage(story, null, List.of(opening));
		assertThat(repository.findRecentMessages(story.getId(), null)).hasSize(1);

		Chat original = save(story, ChatType.STUDENT, "따라갈레요.");
		Chat feedback = save(story, ChatType.FEEDBACK, "‘따라갈래요’라고 써 볼까요?");
		Chat revision = save(story, ChatType.STUDENT, "따라갈래요.");
		Chat reply = save(story, ChatType.STORY, "풀잎 아래 열쇠가 보여요.");
		expectPage(story, null, List.of(opening, original, feedback, revision, reply));
	}

	@Test
	void pagesOnlyThisStoryEvenWhenANewTurnArrives() throws Exception {
		Story story = story(true);
		Story other = story(true);
		List<Chat> saved = new ArrayList<>();
		for (int i = 0; i < 31; i++) {
			saved.add(save(story, ChatType.STORY, "장면 " + i));
			save(other, ChatType.STORY, "다른 이야기");
		}
		var latest = saved.subList(1, 31);
		assertThat(repository.findRecentMessages(story.getId(), null)).containsExactlyElementsOf(latest.reversed());
		expectPage(story, null, latest);
		save(story, ChatType.STUDENT, "앞으로 간다");
		save(story, ChatType.STORY, "다음 장면");
		expectPage(story, latest.getFirst().getId(), List.of(saved.getFirst()));
	}

	@Test
	void returnsAnEmptyPreviousPageAfterExactlyThirtyMessages() throws Exception {
		Story story = story(true);
		List<Chat> saved = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			saved.add(save(story, ChatType.STORY, "장면 " + i));
		}
		expectPage(story, null, saved);
		expectPage(story, saved.getFirst().getId(), List.of());
	}

	@Test
	void hidesDraftsAndMissingStoriesWithTheSameResponse() throws Exception {
		Story draft = story(false);
		save(draft, ChatType.STORY, "비공개");
		for (long id : List.of(draft.getId(), Long.MAX_VALUE)) {
			mvc.perform(get(URL, id)).andExpect(status().isNotFound())
				.andExpect(content().json("""
					{"code":"STORY_NOT_FOUND","message":"이야기를 찾을 수 없습니다.","data":null}
					""", JsonCompareMode.STRICT));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "0", "-1", "9223372036854775808", "0x10", "+1", "1 2", "１２"})
	void rejectsMalformedAndOutOfRangeIds(String value) throws Exception {
		expectInvalid(get(URL, value));
		expectInvalid(get(URL, 1).param("beforeMessageId", value));
	}

	@ParameterizedTest
	@CsvSource({",본문", "STORY,", ","})
	void rejectsIncompleteLatestMessageWithoutReturningAnOlderLastId(ChatType type, String text) throws Exception {
		Story story = story(true);
		Chat opening = save(story, ChatType.STORY, "첫 장면");
		Chat latest = save(story, type, text);
		expectPage(story, latest.getId(), List.of(opening));

		mvc.perform(get(URL, story.getId())).andExpect(status().isInternalServerError())
			.andExpect(content().json("""
				{"code":"INTERNAL_SERVER_ERROR","message":"서버 오류가 발생했습니다.","data":null}
				""", JsonCompareMode.STRICT));
		assertThat(repository.findRecentMessages(story.getId(), null)).containsExactly(latest, opening);
	}

	@Test
	void rejectsAnExplicitlyEmptyCursor() throws Exception {
		expectInvalid(get(URL, 1).param("beforeMessageId", ""));
	}

	private void expectInvalid(MockHttpServletRequestBuilder request) throws Exception {
		mvc.perform(request).andExpect(status().isBadRequest()).andExpect(content().json("""
			{"code":"INVALID_PARAMETER","message":"요청 파라미터의 형식이 올바르지 않습니다.","data":null}
			""", JsonCompareMode.STRICT));
	}

	private void expectPage(Story story, Long cursor, List<Chat> expected) throws Exception {
		var request = get(URL, story.getId());
		if (cursor != null) {
			request.param("beforeMessageId", cursor.toString());
		}
		var items = expected.stream().map(chat -> Map.of(
			"messageId", chat.getId(), "type", chat.getType().name(), "text", chat.getText())).toList();
		var body = Map.of("code", "SUCCESS", "message", "요청에 성공했습니다.", "data", Map.of("messages", items));
		mvc.perform(request).andExpect(status().isOk())
			.andExpect(content().json(json.writeValueAsString(body), JsonCompareMode.STRICT));
	}

	private Story story(boolean published) {
		Record record = new Record("원문", UUID.randomUUID().toString());
		em.persist(record);
		Story story = Story.draft("제목", null, null, null, null, null, record);
		em.persist(story);
		if (published) {
			em.createQuery("update Story s set s.status = :status where s.id = :id")
				.setParameter("status", StoryStatus.PUBLISHED).setParameter("id", story.getId()).executeUpdate();
			em.refresh(story);
		}
		return story;
	}

	private Chat save(Story story, ChatType type, String text) {
		Chat chat = new Chat(type, text, story);
		em.persist(chat);
		return chat;
	}
}
