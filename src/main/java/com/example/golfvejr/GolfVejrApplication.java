package com.example.golfvejr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GolfVejrApplication {

	public static void main(String[] args) {
		SpringApplication.run(GolfVejrApplication.class, args);
	}

}
