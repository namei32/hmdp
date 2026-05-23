package com.hmdp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI hmdpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("黑马点评-接口文档")
                        .description("所有的 API 都会从这里加载")
                        .version("1.0"));
    }
}
