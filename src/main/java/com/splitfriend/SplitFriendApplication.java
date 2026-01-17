package com.splitfriend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SplitFriendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SplitFriendApplication.class, args);
    }
}
