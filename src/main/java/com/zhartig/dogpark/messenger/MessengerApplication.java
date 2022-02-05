package com.zhartig.dogpark.messenger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
@PropertySource("password.properties")
public class MessengerApplication {

    public static void main(String[] args) throws InterruptedException {
      SpringApplication.run(MessengerApplication.class, args);

        while(true) {
            Thread.sleep(10000);
        }
    }

}
