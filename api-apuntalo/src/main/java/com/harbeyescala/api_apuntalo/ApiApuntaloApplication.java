package com.harbeyescala.api_apuntalo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiApuntaloApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApuntaloApplication.class, args);
	}
	
}
