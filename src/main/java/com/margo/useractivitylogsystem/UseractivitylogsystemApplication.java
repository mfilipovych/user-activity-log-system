package com.margo.useractivitylogsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UseractivitylogsystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(UseractivitylogsystemApplication.class, args);
	}

}
