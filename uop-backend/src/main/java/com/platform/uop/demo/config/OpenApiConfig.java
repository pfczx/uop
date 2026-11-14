package com.platform.uop.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI uopOpenAPI() {
    return new OpenAPI()
        .addServersItem(new Server().url("/").description("UOP Platform API"))
        .info(new Info()
            .title("UOP Platform API")
            .version("0.1.0")
            .description("Monitoring platform REST API. Use the Try it out buttons to experiment.")
            .contact(new Contact()
                .name("UOP Team")
                .email("dev@uop.local"))
            .license(new License()
                .name("Private")
                .url("https://uop.local/license")));
  }
}
