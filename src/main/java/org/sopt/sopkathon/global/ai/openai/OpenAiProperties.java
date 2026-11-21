package org.sopt.sopkathon.global.ai.openai;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("app.openai")
public class OpenAiProperties {
	private final String baseUrl;
	private final String apiKey;
	private final int connectTimeoutMs;
	private final int readTimeoutMs;
}
