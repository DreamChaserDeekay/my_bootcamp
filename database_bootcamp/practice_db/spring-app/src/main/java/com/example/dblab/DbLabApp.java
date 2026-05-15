package com.example.dblab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class DbLabApp {
    public static void main(String[] args) {
        SpringApplication.run(DbLabApp.class, args);
    }
}
