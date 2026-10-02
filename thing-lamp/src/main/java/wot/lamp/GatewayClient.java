package wot.lamp;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PreDestroy;

/** Calls to the gateway: registration (POST /things), events (POST /events), unregistration. */
@Component
public class GatewayClient {

    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

    private final RestClient rest;
    private final String selfUrl;
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private volatile boolean registered;

    public GatewayClient(@Value("${gateway.url}") String gatewayUrl,
                         @Value("${gateway.token}") String token,
                         @Value("${thing.base-url}") String selfUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(2000);
        timeouts.setReadTimeout(2000);
        this.rest = RestClient.builder()
                .baseUrl(gatewayUrl)
                .requestFactory(timeouts)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        this.selfUrl = selfUrl;
    }

    // retried every 5 s until the gateway accepts the registration
    @Scheduled(initialDelay = 1000, fixedDelay = 5000)
    public void registerIfNeeded() {
        if (registered) {
            return;
        }
        try {
            rest.post().uri("/things")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("id", LampDescription.ID, "name", "Lamp", "baseUrl", selfUrl,
                            "model", LampDescription.model()))
                    .retrieve()
                    .toBodilessEntity();
            registered = true;
            log.info("registered with the gateway as {}", LampDescription.ID);
        } catch (HttpClientErrorException.Conflict e) {
            // old registration still there: remove it, the next run registers again
            rest.delete().uri("/things/{id}", LampDescription.ID).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("gateway not reachable yet ({}), retrying in 5 s", e.getMessage());
        }
    }

    // sent from a separate thread: the gateway may call the lamp back while handling the event
    public void emit(String type, Map<String, Object> data) {
        String timestamp = Instant.now().toString();
        sender.submit(() -> send(type, data, timestamp));
    }

    private void send(String type, Map<String, Object> data, String timestamp) {
        if (!registered) {
            return;
        }
        try {
            rest.post().uri("/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("thingId", LampDescription.ID, "type", type, "data", data,
                            "timestamp", timestamp))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            registered = false; // gateway restarted: register again
        } catch (RestClientException e) {
            log.warn("event {} not delivered: {}", type, e.getMessage());
        }
    }

    @PreDestroy
    public void unregister() {
        sender.shutdown();
        if (registered) {
            try {
                rest.delete().uri("/things/{id}", LampDescription.ID).retrieve().toBodilessEntity();
            } catch (RestClientException e) {
                log.warn("could not unregister: {}", e.getMessage());
            }
        }
    }
}
