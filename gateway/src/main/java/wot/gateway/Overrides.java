package wot.gateway;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;


@Component
public class Overrides {

    private static final Logger log = LoggerFactory.getLogger(Overrides.class);
    private static final Set<String> CONTROLLED = Set.of("lamp", "thermostat");

    private final TaskScheduler scheduler;
    private final EventHub hub;
    private final Duration t4;
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    private final Map<String, RestartableTimer> timers = new ConcurrentHashMap<>();

    public Overrides(TaskScheduler scheduler, EventHub hub,
                     @Value("${gateway.rules.t4-seconds:600}") long t4Seconds) {
        this.scheduler = scheduler;
        this.hub = hub;
        this.t4 = Duration.ofSeconds(t4Seconds);
    }

    public boolean isActive(String thingId) {
        return active.contains(thingId);
    }

    public void manual(String thingId) {
        if (!CONTROLLED.contains(thingId)) {
            return;
        }
        timers.computeIfAbsent(thingId, id -> new RestartableTimer(scheduler))
                .restart(t4, () -> end(thingId, "T4 elapsed"));
        if (active.add(thingId)) {
            log.info("R4: manual action on {}, automation paused for {} s", thingId, t4.toSeconds());
            announce(thingId);
        }
    }

    public void resume(String thingId) {
        RestartableTimer timer = timers.get(thingId);
        if (timer != null) {
            timer.cancel();
        }
        end(thingId, "resumed by the user");
    }

    private void end(String thingId, String reason) {
        if (active.remove(thingId)) {
            log.info("R4: automation resumes on {} ({})", thingId, reason);
            announce(thingId);
        }
    }

    private void announce(String thingId) {
        hub.broadcast("automation", Map.of("thingId", thingId,
                "automation", isActive(thingId) ? "manual" : "auto"));
    }
}