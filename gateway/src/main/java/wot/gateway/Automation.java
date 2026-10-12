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
    private final Overrides overrides;
    private final RestClient http;
    private final RestartableTimer lampOff;
    private final RestartableTimer energySaving;
    private final Duration t1;
    private final Duration t3;
    private final double comfortTemperature;
    private final double ecoTemperature;

    public Automation(ThingsController things, TaskScheduler scheduler, Overrides overrides,
                      @Value("${gateway.rules.t1-seconds:60}") long t1Seconds,
                      @Value("${gateway.rules.t3-seconds:300}") long t3Seconds,
                      @Value("${gateway.rules.comfort-temperature:19}") double comfortTemperature,
                      @Value("${gateway.rules.eco-temperature:17}") double ecoTemperature) {
        this.things = things;
        this.scheduler = scheduler;
        this.overrides = overrides;
        this.t1 = Duration.ofSeconds(t1Seconds);
        this.t3 = Duration.ofSeconds(t3Seconds);
        this.comfortTemperature = comfortTemperature;
        this.ecoTemperature = ecoTemperature;
        this.lampOff = new RestartableTimer(scheduler);
        this.energySaving = new RestartableTimer(scheduler);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        this.http = RestClient.builder().requestFactory(factory).build();

        armEnergySaving();
    }

    /** Appelé par EventsController : on répond 202 tout de suite, les règles tournent à part. */
    public void onEvent(ThingEvent event) {
        scheduler.schedule(() -> handle(event), Instant.now());
    }

    private synchronized void handle(ThingEvent event) {
        switch (event.type()) {
            case "motion" -> {
                armEnergySaving();
                safely("R3(end)", this::r3End);
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

    // ---------- R3: economie d'energie ----------

    private void armEnergySaving() {
        energySaving.restart(t3, this::r3Timeout);
    }

    private synchronized void r3Timeout() {
        safely("R3", () -> {
            if (overrides.isActive("thermostat")) {
                return;
            }
            put("thermostat", "target", ecoTemperature);
            put("thermostat", "mode", "eco");
            log.info("R3: no motion for {} s, thermostat in eco mode at {} °C", t3.toSeconds(), ecoTemperature);
        });
    }

    private void r3End() {
        if (overrides.isActive("thermostat")) {
            return;
        }
        if ("eco".equals(read("thermostat", "mode"))) {
            put("thermostat", "target", comfortTemperature);
            put("thermostat", "mode", "off");
            log.info("R3: motion detected, energy saving ends");
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