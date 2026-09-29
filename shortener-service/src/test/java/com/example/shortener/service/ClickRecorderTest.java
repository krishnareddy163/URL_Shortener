package com.example.shortener.service;

import com.example.shortener.storage.LinkRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ClickRecorderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-10T09:30:00Z"), ZoneOffset.UTC);

    private final LinkRepository repository = mock(LinkRepository.class);
    private final ClickRecorder recorder = new ClickRecorder(repository, CLOCK);

    @AfterEach
    void stop() throws InterruptedException {
        recorder.stop();
    }

    @Test
    void stoppingARecorderThatNeverStartedIsSafe() {
        assertThatCode(recorder::stop).doesNotThrowAnyException();
    }

    @Test
    void aFailedClickDoesNotStopTheWorker() {
        doThrow(new IllegalStateException("database down")).doNothing()
                .when(repository).recordClick(eq("abc1234"), any());
        recorder.start();

        recorder.enqueue("abc1234");
        recorder.enqueue("abc1234");

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> verify(repository, times(2)).recordClick("abc1234", CLOCK.instant()));
    }

    @Test
    void clicksBeyondCapacityAreDroppedAndTheRestPersisted() {
        doNothing().when(repository).recordClick(any(), any());
        for (int index = 0; index < ClickRecorder.CAPACITY + 1_000; index++) {
            recorder.enqueue("full000");
        }
        recorder.start();

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(repository, times(ClickRecorder.CAPACITY)).recordClick(eq("full000"), any()));
    }

    @Test
    void clicksStillQueuedAtShutdownArePersisted() throws Exception {
        CountDownLatch firstClickStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstClick = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstClickStarted.countDown();
            releaseFirstClick.await(5, TimeUnit.SECONDS);
            return null;
        }).doNothing().when(repository).recordClick(eq("drain01"), any());
        recorder.start();
        recorder.enqueue("drain01");
        firstClickStarted.await(5, TimeUnit.SECONDS);
        recorder.enqueue("drain01");
        recorder.enqueue("drain01");

        Thread stopper = Thread.ofPlatform().start(() -> {
            try {
                recorder.stop();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        });
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> stopper.getState() == Thread.State.TIMED_WAITING);
        releaseFirstClick.countDown();
        stopper.join();

        verify(repository, times(3)).recordClick(eq("drain01"), any());
    }

    @Test
    void anInterruptedWorkerExitsCleanly() {
        recorder.start();
        Thread worker = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals("click-recorder") && thread.isAlive())
                .findFirst().orElseThrow();

        worker.interrupt();

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> !worker.isAlive());
    }
}
