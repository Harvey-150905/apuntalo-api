package com.harbeyescala.api_apuntalo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiApuntaloApplication {

	public static void main(String[] args) {
		System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("admin123"));
		SpringApplication.run(ApiApuntaloApplication.class, args);
	}
	
}
