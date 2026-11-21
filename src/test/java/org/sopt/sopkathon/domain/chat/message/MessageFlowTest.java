package org.sopt.sopkathon.domain.chat.message;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.sopt.sopkathon.domain.chat.entity.Chat;
import org.sopt.sopkathon.domain.chat.entity.ChatType;
import org.sopt.sopkathon.domain.context.entity.Context;
import org.sopt.sopkathon.domain.context.entity.ContextType;
import org.sopt.sopkathon.domain.record.entity.Record;
import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.global.ai.openai.OpenAiChatClient;
import org.sopt.sopkathon.global.ai.openai.OpenAiProperties;
import org.sopt.sopkathon.global.exception.BusinessException;
import org.sopt.sopkathon.global.response.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@SpringBootTest(properties = {"OPENAI_API_KEY=test", "TERRA_INSTRUCTIONS=correction-override",
	"ASTRA_INSTRUCTIONS=scene-test"})
@AutoConfigureMockMvc
class MessageFlowTest {
	private static final String OPENING = "빨간 문만 열면 안 돼.";
	private static final String PROMISE = "토끼는 해 질 때 다리에서 만나기로 했다.";
	private static final String EVENT = "여우가 토끼에게 은열쇠를 맡겼다.";
	private static final ObjectMapper JSON = JsonMapper.builder().build();
	private static final BlockingQueue<String> replies = new LinkedBlockingQueue<>();
	private static final BlockingQueue<JsonNode> requests = new LinkedBlockingQueue<>();
	private static final BlockingQueue<JsonNode> sceneRequests = new LinkedBlockingQueue<>();
	private static final BlockingQueue<CompletableFuture<String>> sceneReplies = new LinkedBlockingQueue<>();
	private static final ExecutorService httpWorkers = Executors.newVirtualThreadPerTaskExecutor();
	private static HttpServer server;

	private static final String CORRECTION_PROMPT = "correction-override";
	@Autowired MockMvc mvc;
	@Autowired EntityManager em;
	@Autowired TransactionTemplate tx;
	@Autowired JdbcTemplate jdbc;
	long storyId;
	long openingId;

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", MessageFlowTest::respond);
		server.setExecutor(httpWorkers);
		server.start();
		registry.add("app.openai.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
	}

	private static void respond(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (!"Bearer test".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
				exchange.sendResponseHeaders(401, -1);
				return;
			}
			JsonNode body = JSON.readTree(exchange.getRequestBody());
			boolean isScene = "story_turn".equals(body.at("/response_format/json_schema/name").asString());
			(isScene ? sceneRequests : requests).add(body);
			String reply;
			try {
				if (isScene) {
					CompletableFuture<String> pending = sceneReplies.poll(5, TimeUnit.SECONDS);
					reply = pending == null ? null : pending.get(5, TimeUnit.SECONDS);
				} else {
					reply = replies.poll(5, TimeUnit.SECONDS);
				}
			} catch (ExecutionException | TimeoutException e) {
				exchange.sendResponseHeaders(500, -1);
				return;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			if (reply == null) {
				exchange.sendResponseHeaders(500, -1);
				return;
			}
			byte[] bytes = JSON.writeValueAsBytes(Map.of("choices", List.of(Map.of(
				"finish_reason", "stop", "message", Map.of("content", reply)))));
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
		}
	}

	@BeforeEach
	void seedStory() {
		requests.clear();
		sceneRequests.clear();
		sceneReplies.clear();
		replies.clear();
		tx.executeWithoutResult(status -> {
			Story story = createStory();
			storyId = story.getId();
			openingId = addChat(story, ChatType.STORY, OPENING);
			em.persist(new Context(ContextType.CHARACTER, "은열쇠는 토끼에게 있다. " + PROMISE, story));
			em.persist(new Context(ContextType.EVENT, EVENT, story));
			Story other = createStory();
			em.persist(new Context(ContextType.EVENT, "다른 이야기", other));
		});
	}

	private Story createStory() {
		Record record = new Record("원문", UUID.randomUUID().toString());
		em.persist(record);
		Story story = new Story("제목", null, "숲", null, "여우", "다리에서 만남", "초안 문구", null, record);
		em.persist(story);
		return story;
	}

	private long addChat(Story story, ChatType type, String text) {
		Chat chat = new Chat(type, text, story);
		em.persist(chat);
		return chat.getId();
	}

	@AfterAll
	static void stopServer() {
		if (server != null) server.stop(0);
		httpWorkers.close();
	}

	private JsonNode send(long story, long seen, String text, int expectedStatus) throws Exception {
		return submit(story, Map.of("text", text, "lastSeenMessageId", seen), expectedStatus);
	}

	private JsonNode submit(long story, Map<String, Object> payload, int expectedStatus) throws Exception {
		var response = mvc.perform(post("/api/v1/stories/{storyId}/messages", story)
			.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsBytes(payload)))
			.andReturn().getResponse();
		assertEquals(expectedStatus, response.getStatus(), response.getContentAsString());
		return JSON.readTree(response.getContentAsByteArray());
	}

	private void passCorrection() {
		replies.add("{\"passed\":true,\"feedback\":null}");
	}

	private void scene(String text, String state, List<String> events) {
		sceneReplies.add(CompletableFuture.completedFuture(JSON.writeValueAsString(new GeneratedScene(text, state, events))));
	}

	private JsonNode nextInput(String model, String instructions) throws Exception {
		var incoming = CORRECTION_PROMPT.equals(instructions) ? requests : sceneRequests;
		JsonNode body = incoming.poll(2, TimeUnit.SECONDS);
		assertNotNull(body);
		assertEquals(model, body.get("model").asString());
		assertEquals(instructions, body.at("/messages/0/content").asString());
		return body.at("/messages/1/content");
	}

	private JsonNode sceneInput() throws Exception {
		return JSON.readTree(nextInput("gpt-6-luna", "scene-test").asString());
	}

	@Test
	void correctionThenProgressPreservesOpeningAndPromisesInActualRequests() throws Exception {
		var delayedScene = new CompletableFuture<String>();
		sceneReplies.add(delayedScene);
		JsonNode corrected;
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var turn = executor.submit(() -> send(storyId, openingId, "따라갈레요", 200));
			assertEquals("따라갈레요", sceneInput().get("studentText").asString());
			replies.add("{\"passed\":false,\"feedback\":\"따라갈래요라고 써 봐요.\"}");
			corrected = turn.get(2, TimeUnit.SECONDS).get("data");
		} finally {
			delayedScene.complete(JSON.writeValueAsString(new GeneratedScene("폐기 장면", "폐기 상태", List.of("폐기 사건"))));
		}
		assertEquals("NEEDS_REVISION", corrected.get("result").asString());
		assertEquals("STUDENT", corrected.at("/messages/0/type").asString());
		assertEquals("FEEDBACK", corrected.at("/messages/1/type").asString());
		nextInput("gpt-6-luna", CORRECTION_PROMPT);
		assertTrue(requests.isEmpty());

		passCorrection();
		String updated = "은열쇠는 여우가 갖고 있다. " + PROMISE;
		scene("토끼가 열쇠를 돌려줬다.", updated, List.of("토끼가 은열쇠를 여우에게 돌려줬다."));
		JsonNode advanced = send(storyId, corrected.at("/messages/1/messageId").asLong(), "여우가요.", 200).get("data");
		assertEquals("STORY_ADVANCED", advanced.get("result").asString());
		assertEquals("STUDENT", advanced.at("/messages/0/type").asString());
		assertEquals("STORY", advanced.at("/messages/1/type").asString());
		assertEquals("여우가요.", nextInput("gpt-6-luna", CORRECTION_PROMPT).asString());
		JsonNode first = sceneInput();
		assertEquals(OPENING, first.get("openingScene").asString());
		assertTrue(first.get("recentTurns").isEmpty());
		assertEquals(JSON.valueToTree(List.of(EVENT)), first.get("events"));
		assertTrue(first.get("characterState").asString().contains(PROMISE));

		passCorrection();
		scene("문이 보인다.", null, List.of());
		send(storyId, advanced.at("/messages/1/messageId").asLong(), "그 문은 왜요?", 200);
		nextInput("gpt-6-luna", CORRECTION_PROMPT);
		JsonNode second = sceneInput();
		assertEquals(OPENING, second.get("openingScene").asString());
		assertEquals(second.toString().indexOf(OPENING), second.toString().lastIndexOf(OPENING));
		assertFalse(second.toString().contains("초안 문구"));
		assertFalse(second.toString().contains("따라갈레요"));
		assertEquals(1, second.get("recentTurns").size());
		assertEquals(updated, second.get("characterState").asString());
		assertEquals(2, second.get("events").size());
		assertEquals(1, countState());
	}

	@Test
	void openingIsIncludedAtTenTurnsAndDropsOutAtEleven() throws Exception {
		long latest = tx.execute(status -> {
			Story story = em.find(Story.class, storyId);
			long last = openingId;
			for (int i = 1; i <= 10; i++) {
				addChat(story, ChatType.STUDENT, "행동" + i);
				last = addChat(story, ChatType.STORY, "장면" + i);
				if (i == 8) {
					addChat(story, ChatType.STUDENT, "교정 대상 원문");
					addChat(story, ChatType.FEEDBACK, "고쳐 쓰세요");
				}
			}
			return last;
		});
		for (int i = 10; i <= 11; i++) {
			passCorrection();
			scene("새 장면", null, List.of());
			latest = send(storyId, latest, "가요", 200).at("/data/messages/1/messageId").asLong();
			nextInput("gpt-6-luna", CORRECTION_PROMPT);
			JsonNode input = sceneInput();
			assertEquals(10, input.get("recentTurns").size());
			assertFalse(input.toString().contains("교정 대상 원문"));
			assertEquals("행동" + (i - 9), input.at("/recentTurns/0/studentText").asString());
			assertEquals(i == 10 ? OPENING : null, input.get("openingScene").asString(null));
			assertEquals(JSON.valueToTree(List.of(EVENT)), input.get("events"));
		}
	}

	@Test
	void rejectsInvalidAndStaleInputsBeforeCallingAi() throws Exception {
		assertEquals("INVALID_INPUT", send(storyId, openingId, " ", 400).get("code").asString());
		assertEquals("INVALID_INPUT", submit(storyId, Map.of("text", "안녕"), 400).get("code").asString());
		assertEquals("INVALID_INPUT", send(storyId, openingId, "🦊".repeat(501), 400).get("code").asString());
		assertEquals("STORY_NOT_FOUND", send(Long.MAX_VALUE, openingId, "안녕", 404).get("code").asString());
		assertEquals("CHAT_OUTDATED", send(storyId, openingId + 1, "안녕", 409).get("code").asString());
		assertTrue(requests.isEmpty());
		sceneReplies.add(CompletableFuture.completedFuture("{}"));
		replies.add("{\"passed\":false,\"feedback\":\"다시 써 봐요.\"}");
		send(storyId, openingId, "🦊".repeat(500), 200);
		sceneInput();
	}

	@Test
	void rejectsConcurrentTurnAndReleasesClaimAfterAiFailure() throws Exception {
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var first = executor.submit(() -> send(storyId, openingId, "가요", 503));
			nextInput("gpt-6-luna", CORRECTION_PROMPT);
			assertEquals("TURN_IN_PROGRESS", send(storyId, openingId, "같이요", 409).get("code").asString());
			sceneInput();
			passCorrection();
			sceneReplies.add(CompletableFuture.completedFuture("{}"));
			assertEquals("AI_UNAVAILABLE", first.get(5, TimeUnit.SECONDS).get("code").asString());
		}
		assertEquals(1, countChats());
		assertEquals(1, countState());
		sceneReplies.add(CompletableFuture.completedFuture("{}"));
		replies.add("{\"passed\":false,\"feedback\":\"수정 예시\"}");
		send(storyId, openingId, "재시도", 200);
		sceneInput();
	}

	@Test
	void createsFirstCharacterStateAndKeepsItWhenThereIsNoChange() throws Exception {
		jdbc.update("delete from context where story_id = ? and context_type = 'CHARACTER'", storyId);
		passCorrection();
		scene("토끼가 다리로 간다.", PROMISE, List.of());
		long latest = send(storyId, openingId, "가요", 200).at("/data/messages/1/messageId").asLong();
		assertEquals(1, countState());
		passCorrection();
		scene("길이 보인다.", null, List.of());
		send(storyId, latest, "길을 봐요", 200);
		assertEquals(PROMISE, jdbc.queryForObject(
			"select context_text from context where story_id = ? and context_type = 'CHARACTER'", String.class, storyId));
	}

	@Test
	void returnsAndStoresCompleteFeedbackBeyondThePromptGuideline() throws Exception {
		String feedback = "가".repeat(121);
		sceneReplies.add(CompletableFuture.completedFuture("{}"));
		replies.add(JSON.writeValueAsString(new StoryAi.Check(false, feedback)));
		JsonNode response = send(storyId, openingId, "되요?", 200);
		sceneInput();
		assertEquals(feedback, response.at("/data/messages/1/text").asString());
		assertEquals(feedback, jdbc.queryForObject(
			"select chat_text from chat where story_id = ? and chat_type = 'FEEDBACK'", String.class, storyId));
		JsonNode body = requests.remove();
		assertEquals(1024, body.get("max_completion_tokens").asInt());
		assertEquals("none", body.get("reasoning_effort").asString());
	}

	@Test
	void mapsReadTimeoutToAiUnavailable() throws IOException {
		HttpServer slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		CountDownLatch release = new CountDownLatch(1);
		slowServer.createContext("/", exchange -> {
			try (exchange) {
				release.await(3, TimeUnit.SECONDS);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		});
		slowServer.start();
		try {
			StoryAi client = new OpenAiStoryAi(new OpenAiChatClient(JSON,
				new OpenAiProperties("http://127.0.0.1:" + slowServer.getAddress().getPort(), "test", 5000, 100)), JSON, "correction-test", "scene-test");
			assertEquals(ErrorCode.AI_UNAVAILABLE, assertThrows(BusinessException.class,
				() -> client.check("안녕")).getErrorCode());
		} finally {
			release.countDown();
			slowServer.stop(0);
		}
	}

	private int countChats() {
		return jdbc.queryForObject("select count(*) from chat where story_id = ?", Integer.class, storyId);
	}

	private int countState() {
		return jdbc.queryForObject("select count(*) from context where context_type = 'CHARACTER' and story_id = ?",
			Integer.class, storyId);
	}

	@Test
	void rollsBackChatsAndCharacterWhenAnEventCannotBeSaved() throws Exception {
		jdbc.execute("alter table context add constraint test_invalid_event check (context_text <> 'invalid-event')");
		try {
			passCorrection();
			scene("장면", "잘못 저장된 현황", List.of("invalid-event"));
			send(storyId, openingId, "행동", 500);
			assertEquals(1, countChats());
			assertTrue(jdbc.queryForObject("select context_text from context where story_id = ? and context_type = 'CHARACTER'",
				String.class, storyId).contains(PROMISE));
		} finally {
			jdbc.execute("alter table context drop constraint test_invalid_event");
		}
	}
}
