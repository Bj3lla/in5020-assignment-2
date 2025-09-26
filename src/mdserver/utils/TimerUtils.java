package mdserver.utils;

import java.util.Timer;
import java.util.TimerTask;

public class TimerUtils {
    public static void schedule(Runnable task, long delayMs) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, delayMs);
    }
}
