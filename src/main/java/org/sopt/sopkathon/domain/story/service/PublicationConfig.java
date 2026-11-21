package org.sopt.sopkathon.domain.story.service;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PublicationProperties.class)
public class PublicationConfig {
	@Bean
	Clock publicationClock(PublicationProperties properties) {
		if (properties.getLease() == null || properties.getLease().isNegative() || properties.getLease().isZero()
			|| properties.getWaitTimeout() == null || properties.getWaitTimeout().isNegative() || properties.getWaitTimeout().isZero()
			|| properties.getPollInterval() == null || properties.getPollInterval().isNegative() || properties.getPollInterval().isZero()) {
			throw new IllegalArgumentException("확정 처리의 유효 기간·대기 시간·조회 간격은 양수여야 합니다.");
		}
		return Clock.systemUTC();
	}
}
