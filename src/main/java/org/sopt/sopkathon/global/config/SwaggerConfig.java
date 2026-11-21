package org.sopt.sopkathon.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("솝커톤 웹 1조 API")
				.description("SOPT 39기 솝커톤 웹 1조 API")
				.version("v1"));
	}
}
