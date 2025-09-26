package mdserver.utils;

import java.util.Timer;
import java.util.TimerTask;

public class TimerUtils {

    // Schedule a single execution after delayMs milliseconds
    public static void schedule(Runnable task, long delayMs) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, delayMs);
    }

    // Schedule repeated execution every intervalMs milliseconds
    public static Timer scheduleAtFixedRate(Runnable task, long delayMs, long intervalMs) {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, delayMs, intervalMs);
        return timer;
    }
}
