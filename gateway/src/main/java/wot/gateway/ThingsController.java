package wot.gateway;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Registry of the things (in memory). */
@RestController
@RequestMapping("/things")
public class ThingsController {

    public record Thing(String id, String name, String baseUrl, Map<String, Object> model) {
    }


    private final Map<String, Thing> things = new ConcurrentHashMap<>();
    private final EventHub hub;
    private final RestClient http;

    public ThingsController(EventHub hub) {
        this.hub = hub;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);   // un thing arrêté doit échouer vite (502)
        factory.setReadTimeout(5000);
        this.http = RestClient.builder().requestFactory(factory).build();
    }
    @PostMapping
    public ResponseEntity<Thing> register(@RequestBody Thing thing) {
        if (thing.id() == null || thing.id().isBlank() || thing.baseUrl() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fields id and baseUrl are required");
        }
        if (things.putIfAbsent(thing.id(), thing) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "thing " + thing.id() + " is already registered");
        }
        hub.broadcast("registry", Map.of("type", "registered", "thingId", thing.id()));
        return ResponseEntity.created(URI.create("/things/" + thing.id())).body(thing);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return things.values().stream()
                .map(t -> Map.<String, Object>of("id", t.id(), "name", String.valueOf(t.name()),
                        "links", Map.of("self", "/things/" + t.id())))
                .toList();
    }

    @GetMapping("/{id}")
    public Thing get(@PathVariable String id) {
        return find(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unregister(@PathVariable String id) {
        find(id);
        things.remove(id);
        hub.broadcast("registry", Map.of("type", "unregistered", "thingId", id));
        return ResponseEntity.noContent().build();
    }

    public Thing find(String id) {
        Thing thing = things.get(id);
        if (thing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "thing " + id + " is not registered");
        }
        return thing;
    }

    // TODO: proxy to thing.baseUrl() with a RestClient (see API conventions)
    //   GET  /things/{id}/properties
    //   GET  /things/{id}/properties/{name}
    //   PUT  /things/{id}/properties/{name}
    //   POST /things/{id}/actions/{name}
    //   POST /things/{id}/automation/resume   (R4, bonus)
    // ---------- Proxy vers les things (Auth Proxy) ----------

    @GetMapping("/{id}/properties")
    public ResponseEntity<String> readAll(@PathVariable String id) {
        return forward(id, HttpMethod.GET, null, "/properties");
    }

    @GetMapping("/{id}/properties/{name}")
    public ResponseEntity<String> read(@PathVariable String id, @PathVariable String name) {
        return forward(id, HttpMethod.GET, null, "/properties/{name}", name);
    }

    @PutMapping("/{id}/properties/{name}")
    public ResponseEntity<String> write(@PathVariable String id, @PathVariable String name,
                                        @RequestBody(required = false) String body) {
        return forward(id, HttpMethod.PUT, body, "/properties/{name}", name);
    }

    @PostMapping("/{id}/actions/{name}")
    public ResponseEntity<String> invoke(@PathVariable String id, @PathVariable String name,
                                         @RequestBody(required = false) String body) {
        return forward(id, HttpMethod.POST, body, "/actions/{name}", name);
    }

    // TODO (R4, bonus) : POST /things/{id}/automation/resume

    /** Relaie la requête au thing et renvoie sa réponse telle quelle (statut + corps). */
    private ResponseEntity<String> forward(String id, HttpMethod method, String body,
                                           String path, Object... vars) {
        Thing thing = find(id); // 404 si inconnu
        try {
            RestClient.RequestBodySpec request = http.method(method).uri(thing.baseUrl() + path, vars);
            if (body != null && !body.isBlank()) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            // exchange() ne lève pas d'exception sur un 4xx/5xx du thing : on relaie sa réponse
            return request.exchange((req, res) -> {
                String text = res.bodyTo(String.class);
                int code = res.getStatusCode().value();
                // erreur d'un thing sans corps JSON (page HTML, vide...) : on fabrique le JSON
                if (code >= 400 && (text == null || !text.trim().startsWith("{"))) {
                    text = "{\"status\":" + code + ",\"error\":\"thing " + id + " answered with an error\"}";
                }
                return ResponseEntity.status(res.getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(text);
            });
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "thing " + id + " does not answer");
        }
    }
    // thing not answering: 502
}
