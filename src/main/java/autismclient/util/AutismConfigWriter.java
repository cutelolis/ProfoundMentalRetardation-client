package autismclient.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class AutismConfigWriter {
    private static final AtomicReference<String> PENDING = new AtomicReference<>();
    private static volatile ExecutorService executor;

    private AutismConfigWriter() {
    }

    static void enqueue(String json) {
        PENDING.set(json);
        ensureExecutor().execute(AutismConfigWriter::drain);
    }

    static void flushBlocking(long timeoutMs) {
        ExecutorService running = executor;
        if (running == null) return;
        CountDownLatch latch = new CountDownLatch(1);
        try {
            running.execute(latch::countDown);
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {

        }
    }

    private static void drain() {
        String json = PENDING.getAndSet(null);
        if (json == null) return;
        AutismConfig.writeToDisk(json);
    }

    private static ExecutorService ensureExecutor() {
        ExecutorService running = executor;
        if (running != null) return running;
        synchronized (AutismConfigWriter.class) {
            if (executor == null) {
                executor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "autism-config-io");
                    t.setDaemon(true);
                    return t;
                });
            }
            return executor;
        }
    }
}
