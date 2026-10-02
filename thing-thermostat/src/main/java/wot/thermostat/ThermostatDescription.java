package wot.thermostat;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web Thing Model of the thermostat (GET /model). */
public final class ThermostatDescription {

    public static final String ID = "thermostat";

    private ThermostatDescription() {
    }

    public static Map<String, Object> model() {
        Map<String, Object> model = new LinkedHashMap<>();

        model.put("id", ID);
        model.put("name", "Thermostat");
        model.put("description", "Virtual thermostat of the Smart Lab");

        model.put("properties", Map.of(
                "temperature", Map.of(
                        "type", "number",
                        "description", "current temperature in °C",
                        "readOnly", true
                ),
                "target", Map.of(
                        "type", "number",
                        "description", "target temperature in °C"
                ),
                "mode", Map.of(
                        "type", "string",
                        "description", "thermostat mode",
                        "enum", java.util.List.of("off", "heat", "eco")
                )
        ));

        model.put("actions", Map.of(
                "setTarget", Map.of(
                        "description", "sets the target temperature"
                )
        ));

        model.put("events", Map.of(
                "targetReached", Map.of(
                        "description", "emitted when the target temperature is reached"
                ),
                "propertyChanged", Map.of(
                        "description", "a property changed: {\"property\": ..., \"value\": ...}"
                )
        ));

        model.put("links", Map.of(
                "self", "/model",
                "properties", "/properties",
                "setTarget", "/actions/setTarget"
        ));

        return model;
    }
}