package com.example.shortener.service;

import com.example.shortener.storage.LinkRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records clicks asynchronously so the redirect path never blocks on persistence.
 *
 * <p>Clicks go into a bounded queue (capacity {@value #CAPACITY}) drained by one worker thread. When
 * the queue is full the click is dropped and counted in {@link #droppedClicks()}.
 */
@Component
public class ClickRecorder {
    public static final int CAPACITY = 10_000;
    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

    private final LinkRepository repository;
    private final Clock clock;
    private final BlockingQueue<Click> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private volatile boolean running;
    private Thread worker;

    public ClickRecorder(LinkRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        running = true;
        worker = Thread.ofPlatform().daemon().name("click-recorder").start(this::drain);
    }

    @PreDestroy
    void stop() throws InterruptedException {
        running = false;
        if (worker != null) {
            worker.join(TimeUnit.SECONDS.toMillis(2));
        }
    }

    /** Enqueues a click without blocking; drops and counts it if the queue is full. */
    public void enqueue(String code) {
        if (!queue.offer(new Click(code, clock.instant()))) {
            long total = dropped.incrementAndGet();
            if (total == 1 || total % 1_000 == 0) {
                log.warn("Click queue full; dropped clicks so far: {}", total);
            }
        }
    }

    /** Number of clicks dropped because the queue was full. */
    public long droppedClicks() {
        return dropped.get();
    }

    /** Number of clicks that could not be persisted. */
    public long failedClicks() {
        return failed.get();
    }

    private void drain() {
        while (running || !queue.isEmpty()) {
            try {
                Click click = queue.poll(100, TimeUnit.MILLISECONDS);
                if (click != null) {
                    persist(click);
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void persist(Click click) {
        try {
            repository.recordClick(click.code(), click.clickedAt());
        } catch (RuntimeException exception) {
            failed.incrementAndGet();
            log.warn("Failed to persist click for code {}: {}", LogSanitizer.clean(click.code()), exception.getClass().getSimpleName());
        }
    }

    private record Click(String code, Instant clickedAt) {
    }
}
