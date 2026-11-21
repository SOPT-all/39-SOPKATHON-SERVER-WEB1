package org.sopt.sopkathon.infrastructure.openai;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.openai")
public class OpenAiProperties {
	private String apiKey = "";
	private String model = "";
	private Duration connectTimeout = Duration.ofSeconds(5);
	private Duration readTimeout = Duration.ofSeconds(60);
	private int maxOutputTokens = 4096;
}
