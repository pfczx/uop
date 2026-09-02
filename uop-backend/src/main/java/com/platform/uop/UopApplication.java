package com.platform.uop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.platform.uop.users.bootstrap.AdminBootstrapProperties;

@SpringBootApplication
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class UopApplication {

	public static void main(String[] args) {
		SpringApplication.run(UopApplication.class, args);
	}

}
