package com.example.dawanow;

import com.example.dawanow.config.notification.FcmClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DawaNowApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(DawaNowApplication.class, args);
    }

}
