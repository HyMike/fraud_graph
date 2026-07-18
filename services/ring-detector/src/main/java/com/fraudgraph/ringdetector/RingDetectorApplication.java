package com.fraudgraph.ringdetector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RingDetectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(RingDetectorApplication.class, args);
	}
}
