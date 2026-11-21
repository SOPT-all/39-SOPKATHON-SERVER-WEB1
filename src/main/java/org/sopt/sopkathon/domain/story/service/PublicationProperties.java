package org.sopt.sopkathon.domain.story.service;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.story-publication")
public class PublicationProperties {
	private Duration lease = Duration.ofSeconds(120);
	private Duration waitTimeout = Duration.ofSeconds(75);
	private Duration pollInterval = Duration.ofMillis(100);
}
