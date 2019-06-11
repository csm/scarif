package scarif;

import com.netflix.config.FixedDelayPollingScheduler;

import java.util.concurrent.atomic.AtomicReference;

public class TriggerableFixedDelayPollingScheduler extends FixedDelayPollingScheduler {
    private final AtomicReference<Runnable> runnerReference = new AtomicReference<>(null);

    public TriggerableFixedDelayPollingScheduler() {
        super();
    }

    public TriggerableFixedDelayPollingScheduler(int initialDelayMillis, int delayMillis, boolean ignoreDeletesFromSource) {
        super(initialDelayMillis, delayMillis, ignoreDeletesFromSource);
    }

    protected synchronized void schedule(Runnable runnable) {
        runnerReference.set(runnable);
        super.schedule(runnable);
    }

    public void triggerNow() {
        Runnable runner = runnerReference.get();
        if (runner != null) {
            try {
                runner.run();
            } catch (Throwable t) {
                // ignored
            }
        }
    }
}
