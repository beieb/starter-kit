package wot.motion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Motion Sensor (port 8083). */
@SpringBootApplication
@EnableScheduling
public class MotionSensorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MotionSensorApplication.class, args);
    }
}
