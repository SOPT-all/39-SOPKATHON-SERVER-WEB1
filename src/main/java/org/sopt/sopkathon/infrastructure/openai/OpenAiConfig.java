package org.sopt.sopkathon.infrastructure.openai;

import java.net.http.HttpClient;
import org.sopt.sopkathon.domain.story.service.StoryDraftGenerator;
import org.sopt.sopkathon.domain.story.service.StoryOpeningGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {
	@Bean
	OpenAiResponsesClient openAiResponsesClient(OpenAiProperties properties, ObjectMapper objectMapper) {
		if (properties.getConnectTimeout() == null || properties.getConnectTimeout().isZero()
			|| properties.getConnectTimeout().isNegative() || properties.getReadTimeout() == null
			|| properties.getReadTimeout().isZero() || properties.getReadTimeout().isNegative()
			|| properties.getMaxOutputTokens() <= 0) {
			throw new IllegalArgumentException("OpenAI 연결·응답 제한 시간과 토큰 상한은 양수여야 합니다.");
		}
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getReadTimeout());
		RestClient client = RestClient.builder().baseUrl("https://api.openai.com/v1")
			.requestFactory(requestFactory).build();
		return new OpenAiResponsesClient(client, properties, objectMapper);
	}

	@Bean
	StoryDraftGenerator storyDraftGenerator(OpenAiResponsesClient responses) {
		return new OpenAiStoryDraftGenerator(responses);
	}

	@Bean
	StoryOpeningGenerator storyOpeningGenerator(OpenAiResponsesClient responses, ObjectMapper objectMapper) {
		return new OpenAiStoryOpeningGenerator(responses, objectMapper);
	}
}
