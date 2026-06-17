package com.innerstyle.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI innerStyleOpenApi() {
        return new OpenAPI().info(new Info()
            .title("InnerStyle API")
            .version("v1")
            .description("2D image / text to animated 3D model module powered by MeshyAI "
                + "(image-to-3D, text-to-3D, texture/color, optimization, rigging, animation, pose).")
            .license(new License().name("Proprietary")));
    }
}
