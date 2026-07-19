package com.softbei.scenicai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ScenicAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScenicAiApplication.class, args);
    }
}
