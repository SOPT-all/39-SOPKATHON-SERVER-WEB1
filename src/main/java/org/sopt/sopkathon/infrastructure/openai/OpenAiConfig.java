package org.sopt.sopkathon.infrastructure.openai;

import java.net.http.HttpClient;
import org.sopt.sopkathon.domain.story.service.StoryDraftGenerator;
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
	StoryDraftGenerator storyDraftGenerator(OpenAiProperties properties, ObjectMapper objectMapper) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getReadTimeout());
		RestClient client = RestClient.builder().baseUrl("https://api.openai.com/v1")
			.requestFactory(requestFactory).build();
		return new OpenAiStoryDraftGenerator(client, properties, objectMapper);
	}
}
