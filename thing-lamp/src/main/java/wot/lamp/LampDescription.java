package wot.lamp;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web Thing Model of the lamp (GET /model). */
public final class LampDescription {

    public static final String ID = "lamp";

    private LampDescription() {
    }

    public static Map<String, Object> model() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", ID);
        model.put("name", "Lamp");
        model.put("description", "Virtual lamp of the Smart Lab");
        model.put("properties", Map.of(
                "on", Map.of("type", "boolean", "description", "true when the lamp is switched on")
                // TODO: brightness
        ));
        model.put("actions", Map.of(
                "toggle", Map.of("description", "switches the lamp on or off")
                // TODO: setBrightness
        ));
        model.put("events", Map.of(
                "propertyChanged", Map.of("description", "a property changed: {\"property\": ..., \"value\": ...}")));
        model.put("links", Map.of(
                "self", "/model",
                "properties", "/properties",
                "toggle", "/actions/toggle"));
        return model;
    }
}
