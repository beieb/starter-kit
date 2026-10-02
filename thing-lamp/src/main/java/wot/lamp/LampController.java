package wot.lamp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** GET /model, GET /properties, GET|PUT /properties/{name}, POST /actions/{name}. */
@RestController
public class LampController {

    private final Map<String, Object> state = new ConcurrentHashMap<>(Map.of("on", false, "brightness", 50));
    private final GatewayClient gateway;

    public LampController(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() {
        return LampDescription.model();
    }

    @GetMapping("/properties")
    public Map<String, Object> properties() {
        return state;
    }

    @GetMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> read(@PathVariable String name) {
        if (!state.containsKey(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        return ResponseEntity.ok(Map.of("name", name, "value", state.get(name)));
    }

    @PutMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> write(@PathVariable String name, @RequestBody Map<String, Object> body) {
        if (!state.containsKey(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        Object value = body.get("value");
        if(name.equals("brightness")) {
            if(!(value instanceof Integer s)) {
                return error(HttpStatus.BAD_REQUEST, "property on expects a number {\"brightness\": 0-100");
            }
            if (!validBrightness(value)) {
                return error(HttpStatus.BAD_REQUEST, "setBrightness expects {\"value\": number between 0 and 100} "+ value+" type "+ value.getClass());
            }
            set("brightness", s);
            return ResponseEntity.ok(Map.of("name", "brightness", "value", s));

        }
        if (!(value instanceof Boolean)) {
            return error(HttpStatus.BAD_REQUEST, "property on expects a boolean {\"value\": true|false}");
        }
        set(name, value);
        return ResponseEntity.ok(Map.of("name", name, "value", value));
    }

    // the rules must write the property on, not call toggle
    @PostMapping("/actions/toggle")
    public Map<String, Object> toggle() {
        set("on", !(Boolean) state.get("on"));
        return Map.of("action", "toggle", "status", "completed", "properties", state);
    }
    

    // TODO: property brightness (0..100) and action setBrightness
    @PostMapping("/actions/setBrightness")
    public ResponseEntity<Map<String, Object>> setBrightness(@RequestBody Map<String, Object> body) {
        Object value = body.get("value");
        if (!validBrightness(value)) {
            return error(HttpStatus.BAD_REQUEST, "setBrightness expects {\"value\": number between 0 and 100} "+ value+" type "+ value.getClass());
        }
        set("Brightness", ((Number) value).doubleValue());
        return ResponseEntity.ok(Map.of("action", "setBrightness", "status", "completed", "properties", state));
    }

    private static boolean validBrightness(Object value) {
        return value instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() <= 100;
    }

    private void set(String name, Object value) {
        state.put(name, value);
        gateway.emit("propertyChanged", Map.of("property", name, "value", value));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
