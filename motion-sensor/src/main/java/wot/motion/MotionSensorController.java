package wot.motion;

import java.time.Instant;
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
public class MotionSensorController {

    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private final GatewayClient gateway;

    public MotionSensorController(GatewayClient gateway) {
        this.gateway = gateway;
        this.state.put("lastMotion", Instant.now().toString());
    }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() {
        return MotionSensorDescription.model();
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
    public ResponseEntity<Map<String, Object>> write(@PathVariable String name,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        if (!state.containsKey(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        return error(HttpStatus.BAD_REQUEST, "property " + name + " is read-only");
    }

    @PostMapping("/actions/simulateMotion")
    public ResponseEntity<Map<String, Object>> simulateMotion() {
        String now = Instant.now().toString();
        state.put("lastMotion", now);
        gateway.emit("motion", Map.of("timestamp", now));
        return ResponseEntity.ok(Map.of("action", "simulateMotion", "status", "completed", "properties", state));
    }

    @PostMapping("/actions/{name}")
    public ResponseEntity<Map<String, Object>> unknownAction(@PathVariable String name) {
        if ("simulateMotion".equals(name)) {
            return simulateMotion();
        }
        return error(HttpStatus.NOT_FOUND, "unknown action " + name);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
