package com.example.fakebank;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration")
@EnableConfigurationProperties(BankConfig.class)
public class FakeBankApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder(FakeBankApplication.class)
				.properties("spring.config.name=bank")
				.run(args);
	}

}
