package com.example.safari;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SafariApplication {
	public static void main(String[] args) {
		SpringApplication.run(SafariApplication.class, args);
	}

}
