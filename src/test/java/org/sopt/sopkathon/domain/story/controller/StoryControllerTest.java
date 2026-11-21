package org.sopt.sopkathon.domain.story.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sopt.sopkathon.domain.record.entity.Record;
import org.sopt.sopkathon.domain.record.repository.RecordRepository;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.domain.story.entity.StoryCategory;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;
import org.sopt.sopkathon.domain.story.repository.StoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoryControllerTest {
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private MockMvc mvc;
	@Autowired
	private StoryRepository storyRepository;
	@Autowired
	private RecordRepository recordRepository;
	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void cleanDatabase() {
		jdbc.update("delete from chat");
		jdbc.update("delete from context");
		storyRepository.deleteAll();
		recordRepository.deleteAll();
	}

	@Test
	void returnsOnlyPublishedStoriesNewestFirst() throws Exception {
		Story older = save("오래된 공개", StoryCategory.ROMANCE, StoryStatus.PUBLISHED, "older-uuid");
		save("초안", StoryCategory.ETC, StoryStatus.DRAFT, "draft-uuid");
		Story newer = save("최신 공개", StoryCategory.FANTASY, StoryStatus.PUBLISHED, "newer-uuid");

		JsonNode root = JSON.readTree(requestStories());
		JsonNode items = root.get("data").get("items");

		assertEquals("SUCCESS", root.get("code").asString());
		assertEquals("요청에 성공했습니다.", root.get("message").asString());
		assertEquals(2, items.size());
		assertEquals(newer.getId(), items.get(0).get("storyId").asLong());
		assertEquals(older.getId(), items.get(1).get("storyId").asLong());
		assertEquals("FANTASY", items.get(0).get("category").asString());
	}

	@Test
	void returnsEmptyItemsWhenNoStoryIsPublished() throws Exception {
		save("초안", StoryCategory.ETC, StoryStatus.DRAFT, "draft-uuid");

		JsonNode root = JSON.readTree(requestStories());

		assertEquals(0, root.get("data").get("items").size());
	}

	@Test
	void exposesOnlyPublicFields() throws Exception {
		save("공개", StoryCategory.HORROR, StoryStatus.PUBLISHED, "secret-uuid");

		String body = requestStories();
		JsonNode item = JSON.readTree(body).get("data").get("items").get(0);

		assertEquals(Set.of("storyId", "title", "summary", "imageUrl", "category"),
			new TreeSet<>(item.propertyNames()));
		assertFalse(body.contains("secret"));
	}

	@Test
	void returnsPublishedStoryDetailWithPublicFieldsOnly() throws Exception {
		Story story = save("공개", StoryCategory.FANTASY, StoryStatus.PUBLISHED, "hidden-uuid");

		MockHttpServletResponse response = mvc.perform(get("/api/v1/stories/{storyId}", story.getId()))
			.andReturn().getResponse();
		String body = response.getContentAsString(StandardCharsets.UTF_8);
		JsonNode root = JSON.readTree(body);
		JsonNode data = root.get("data");

		assertEquals(200, response.getStatus());
		assertEquals("SUCCESS", root.get("code").asString());
		assertEquals(Set.of("storyId", "title", "summary", "imageUrl", "category", "setting", "characters"),
			new TreeSet<>(data.propertyNames()));
		assertEquals(story.getId(), data.get("storyId").asLong());
		assertEquals("FANTASY", data.get("category").asString());
		assertEquals("secret-background", data.get("setting").asString());
		assertEquals("secret-character", data.get("characters").asString());
		assertFalse(body.contains("secret-event"));
		assertFalse(body.contains("secret-prologue"));
		assertFalse(body.contains("secret-transcript"));
		assertFalse(body.contains("hidden-uuid"));
	}

	@Test
	void hidesDraftStoryDetail() throws Exception {
		Story draft = save("초안", StoryCategory.ETC, StoryStatus.DRAFT, "draft-uuid");

		assertError("/api/v1/stories/" + draft.getId(), 404, "STORY_NOT_FOUND");
	}

	@Test
	void returnsNotFoundForMissingStory() throws Exception {
		assertError("/api/v1/stories/" + Long.MAX_VALUE, 404, "STORY_NOT_FOUND");
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
	void rejectsInvalidStoryId(String storyId) throws Exception {
		assertError("/api/v1/stories/" + storyId, 400, "INVALID_PARAMETER");
	}

	private void assertError(String path, int status, String code) throws Exception {
		MockHttpServletResponse response = mvc.perform(get(path)).andReturn().getResponse();
		JsonNode root = JSON.readTree(response.getContentAsString(StandardCharsets.UTF_8));

		assertEquals(status, response.getStatus());
		assertEquals(code, root.get("code").asString());
		assertTrue(root.get("data").isNull());
	}

	private String requestStories() throws Exception {
		MockHttpServletResponse response = mvc.perform(get("/api/v1/stories")).andReturn().getResponse();
		assertEquals(200, response.getStatus());
		return response.getContentAsString(StandardCharsets.UTF_8);
	}

	private Story save(String title, StoryCategory category, StoryStatus status, String uuid) {
		Record record = recordRepository.save(new Record("secret-transcript", uuid));
		Story story = new Story(title, category, "secret-background", "공개 소개문", "secret-character",
			"secret-event", "secret-prologue", "https://example.com/story.jpg", record);
		ReflectionTestUtils.setField(story, "status", status);
		return storyRepository.save(story);
	}
}
