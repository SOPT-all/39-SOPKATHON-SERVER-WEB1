package org.sopt.sopkathon.domain.chat.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:seed-chat;MODE=MySQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.defer-datasource-initialization=true",
	"spring.sql.init.mode=always"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SeedChatFlowTest {
	@Autowired MockMvc mvc;
	@Autowired ObjectMapper json;
	@MockitoBean StoryAi ai;

	@ParameterizedTest
	@ValueSource(ints = {1, 2, 3, 4, 5, 6})
	void repliesFromSeededHistory(int storyId) throws Exception {
		String path = "/api/v1/stories/" + storyId + "/messages";
		JsonNode before = history(path);
		assertEquals(storyId == 1 ? 11 : 1, before.size());
		assertEquals("STORY", before.get(0).get("type").asString());
		long last = before.get(before.size() - 1).get("messageId").asLong();

		when(ai.check(any())).thenReturn(new StoryAi.Check(true, null));
		when(ai.generate(any())).thenReturn(new GeneratedScene("새로운 장면", null, List.of()));
		var result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
			.content("{\"text\":\"주변을 살펴볼게요.\",\"lastSeenMessageId\":" + last + "}"))
			.andExpect(status().isOk()).andReturn();
		JsonNode data = json.readTree(result.getResponse().getContentAsByteArray()).get("data");
		assertEquals("STORY_ADVANCED", data.get("result").asString());
		JsonNode after = history(path);
		assertEquals(before.size() + 2, after.size());
		assertEquals(data.at("/messages/0"), after.get(before.size()));
		assertEquals(data.at("/messages/1"), after.get(before.size() + 1));
	}

	private JsonNode history(String path) throws Exception {
		var result = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
		return json.readTree(result.getResponse().getContentAsByteArray()).at("/data/messages");
	}
}
