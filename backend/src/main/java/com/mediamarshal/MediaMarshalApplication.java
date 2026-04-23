package com.mediamarshal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MediaMarshalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaMarshalApplication.class, args);
    }
}
