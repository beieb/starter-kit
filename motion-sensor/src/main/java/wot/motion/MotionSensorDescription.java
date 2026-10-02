package wot.motion;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web Thing Model of the motion sensor (GET /model). */
public final class MotionSensorDescription {

    public static final String ID = "motion";

    private MotionSensorDescription() {
    }

    public static Map<String, Object> model() {
        Map<String, Object> model = new LinkedHashMap<>();

        model.put("id", ID);
        model.put("name", "Motion Sensor");
        model.put("description", "Virtual motion sensor of the Smart Lab");

        model.put("properties", Map.of(
                "lastMotion", Map.of(
                        "type", "string",
                        "format", "date-time",
                        "description", "timestamp of the last detected motion",
                        "readOnly", true
                )
        ));

        model.put("actions", Map.of(
                "simulateMotion", Map.of(
                        "description", "simulates a detected motion"
                )
        ));

        model.put("events", Map.of(
                "motion", Map.of(
                        "description", "emitted when motion is detected"
                )
        ));

        model.put("links", Map.of(
                "self", "/model",
                "properties", "/properties",
                "simulateMotion", "/actions/simulateMotion"
        ));

        return model;
    }
}
