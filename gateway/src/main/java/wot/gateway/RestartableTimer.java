package wot.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import org.springframework.scheduling.TaskScheduler;

/**
 * Timer that can be restarted, for the rules (e.g. R1: lamp off after T1 without motion).
 * Usage: lampOff.restart(Duration.ofSeconds(10), () -> ...); the TaskScheduler is injected by Spring.
 * The task runs in a scheduler thread.
 */
public class RestartableTimer {

    private final TaskScheduler scheduler;
    private ScheduledFuture<?> pending;

    public RestartableTimer(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public synchronized void restart(Duration delay, Runnable task) {
        cancel();
        pending = scheduler.schedule(task, Instant.now().plus(delay));
    }

    public synchronized void cancel() {
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }
}
