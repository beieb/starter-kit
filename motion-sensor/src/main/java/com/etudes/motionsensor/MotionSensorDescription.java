package com.etudes.motionsensor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web Thing Model of the lamp (GET /model). */
public final class MotionSensorDescription {

    public static final String ID = "motion";

    private MotionSensorDescription() {
    }

    public static Map<String, Object> model() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", ID);
        model.put("name", "MotionSensor");
        model.put("description", "Motion sensor of the Smart Lab");
        model.put("properties", Map.of(
                "lastMotion", Map.of("type", "timestamp", "description", "lastest motion")
        ));
        model.put("actions", Map.of(
                "simulateMotion", Map.of("description", "Simulate a motion")
        ));
        model.put("events", Map.of(
                "motion", Map.of("description", "Something moved")));
        model.put("links", Map.of(
                "self", "/model",
                "properties", "/properties",
                "toggle", "/actions/toggle"));
        return model;
    }
}
