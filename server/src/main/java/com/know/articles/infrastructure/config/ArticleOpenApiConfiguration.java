package com.know.articles.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ArticleOpenApiConfiguration {
  @Bean
  OpenAPI articleOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Know Article API")
                .version("v1")
                .description(
                    "Save, update and read account-owned articles. Real authentication is implemented in the security checkpoint."))
        .servers(List.of(new Server().url("/")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "knowBearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .security(List.of(new SecurityRequirement().addList("knowBearer")));
  }
}
