package wot.thermostat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** GET /model, GET /properties, GET|PUT /properties/{name}, POST /actions/setTarget. */
@RestController
public class ThermostatController {

    private static final Set<String> MODES = Set.of("off", "heat", "eco");
    private static final double AMBIENT = 15.0;

    private final Map<String, Object> state = new ConcurrentHashMap<>(
            Map.of("temperature", 18.0, "target", 20.0, "mode", "eco"));
    private final GatewayClient gateway;

    public ThermostatController(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() {
        return ThermostatDescription.model();
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
        if (name.equals("temperature")) {
            return error(HttpStatus.BAD_REQUEST, "property temperature is read-only");
        }
        Object value = body.get("value");
        if (name.equals("mode")) {
            if (!(value instanceof String s) || !MODES.contains(s)) {
                return error(HttpStatus.BAD_REQUEST, "mode must be one of " + MODES);
            }
            set("mode", s);
            return ResponseEntity.ok(Map.of("name", "mode", "value", s));
        }
        if (!validTarget(value)) {
            return error(HttpStatus.BAD_REQUEST, "target expects a number between 5 and 30");
        }
        double target = ((Number) value).doubleValue();
        set("target", target);
        return ResponseEntity.ok(Map.of("name", "target", "value", target));
    }

    @PostMapping("/actions/setTarget")
    public ResponseEntity<Map<String, Object>> setTarget(@RequestBody Map<String, Object> body) {
        Object value = body.get("value");
        if (!validTarget(value)) {
            return error(HttpStatus.BAD_REQUEST, "setTarget expects {\"value\": number between 5 and 30}");
        }
        set("target", ((Number) value).doubleValue());
        return ResponseEntity.ok(Map.of("action", "setTarget", "status", "completed", "properties", state));
    }

    // simulation: every 2 s the temperature moves by 0.5 °C towards the target (or towards ambient if off)
    @Scheduled(initialDelay = 2000, fixedDelay = 2000)
    public void simulate() {
        double current = (Double) state.get("temperature");
        double goal = "off".equals(state.get("mode")) ? AMBIENT : (Double) state.get("target");
        double next = Math.abs(goal - current) <= 0.5 ? goal : current + Math.signum(goal - current) * 0.5;
        if (next != current) {
            set("temperature", next);
        }
    }

    private static boolean validTarget(Object value) {
        return value instanceof Number n && n.doubleValue() >= 5 && n.doubleValue() <= 30;
    }

    private void set(String name, Object value) {
        state.put(name, value);
        gateway.emit("propertyChanged", Map.of("property", name, "value", value));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
