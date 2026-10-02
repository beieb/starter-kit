package wot.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import wot.gateway.EventsController.ThingEvent;

/**
 * Règles d'automatisation évaluées par le gateway.
 * Les requêtes partent directement vers les things (pas par le proxy), donc elles ne comptent
 * jamais comme des actions manuelles (R4).
 */
@Component
public class Automation {

    private static final Logger log = LoggerFactory.getLogger(Automation.class);

    private final ThingsController things;
    private final TaskScheduler scheduler;
    private final RestClient http;
    private final RestartableTimer lampOff;
    private final Duration t1;
    private final double comfortTemperature;

    public Automation(ThingsController things, TaskScheduler scheduler,
                      @Value("${gateway.rules.t1-seconds:60}") long t1Seconds,
                      @Value("${gateway.rules.comfort-temperature:19}") double comfortTemperature) {
        this.things = things;
        this.scheduler = scheduler;
        this.t1 = Duration.ofSeconds(t1Seconds);
        this.comfortTemperature = comfortTemperature;
        this.lampOff = new RestartableTimer(scheduler);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Appelé par EventsController : on répond 202 tout de suite, les règles tournent à part. */
    public void onEvent(ThingEvent event) {
        scheduler.schedule(() -> handle(event), Instant.now());
    }

    private synchronized void handle(ThingEvent event) {
        switch (event.type()) {
            case "motion" -> {
                // ordre imposé par le sujet : fin de R3 (bonus), puis R1, puis R2
                safely("R1", this::r1OnMotion);
                safely("R2", this::r2OnMotion);
            }
            case "targetReached" -> safely("R2", this::r2OnTargetReached);
            default -> { }
        }
    }

    // ---------- R1 : éclairage au mouvement ----------

    private void r1OnMotion() {
        put("lamp", "on", true);
        // chaque nouveau mouvement relance le compte à rebours de T1
        lampOff.restart(t1, () -> safely("R1 (lamp off)", () -> put("lamp", "on", false)));
        log.info("R1: lamp on, off in {} s without motion", t1.toSeconds());
    }

    // ---------- R2 : confort thermique ----------

    private void r2OnMotion() {
        Object value = read("thermostat", "temperature");
        if (value instanceof Number temperature && temperature.doubleValue() < comfortTemperature) {
            put("thermostat", "target", comfortTemperature);
            put("thermostat", "mode", "heat");
            log.info("R2: temperature {} < {}, heating on", temperature, comfortTemperature);
        }
    }

    private void r2OnTargetReached() {
        // en mode eco, targetReached est ignoré : seule la chauffe lancée par R2 (heat) s'arrête
        if ("heat".equals(read("thermostat", "mode"))) {
            put("thermostat", "mode", "off");
            log.info("R2: target reached, heating off");
        }
    }

    // ---------- appels directs aux things ----------

    private void put(String thingId, String property, Object value) {
        String baseUrl = things.find(thingId).baseUrl();
        http.put().uri(baseUrl + "/properties/{name}", property)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("value", value))
                .retrieve().toBodilessEntity();
    }

    private Object read(String thingId, String property) {
        String baseUrl = things.find(thingId).baseUrl();
        Map<?, ?> response = http.get().uri(baseUrl + "/properties/{name}", property)
                .retrieve().body(Map.class);
        return response == null ? null : response.get("value");
    }

    /** Une règle qui échoue (thing absent, hors ligne...) ne doit pas bloquer les autres. */
    private void safely(String rule, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("{} failed: {}", rule, e.getMessage());
        }
    }
}