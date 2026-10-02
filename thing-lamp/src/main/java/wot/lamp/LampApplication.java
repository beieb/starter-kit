package wot.lamp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Lamp (port 8082). Copy this module for the thermostat (8081) and the motion sensor (8083). */
@SpringBootApplication
@EnableScheduling
public class LampApplication {

    public static void main(String[] args) {
        SpringApplication.run(LampApplication.class, args);
    }
}
