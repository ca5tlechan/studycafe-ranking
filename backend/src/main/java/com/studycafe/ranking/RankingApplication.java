package com.studycafe.ranking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 04:00 자동 마감 배치(§3.6a)
@EnableAsync      // 기동 catch-up 을 백그라운드로(StartupCatchUp) — readiness 를 막지 않게
public class RankingApplication {

	public static void main(String[] args) {
		SpringApplication.run(RankingApplication.class, args);
	}

}
