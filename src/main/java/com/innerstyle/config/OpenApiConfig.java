package com.innerstyle.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI innerStyleOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("InnerStyle API")
                .version("v1")
                .description("2D image / text to animated 3D model module powered by MeshyAI "
                    + "(image-to-3D, text-to-3D, texture/color, optimization, rigging, animation, pose). "
                    + "Includes auth (JWT, social login, email verify, password reset).")
                .license(new License().name("Proprietary")))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste the access token returned by /api/user/auth/login")));
    }
}
