package com.example.agentic.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Exclusive, per-run writer lock ({@code runs/<id>/run.lock}, an OS file lock). Only one process may change a run
 * at a time: two concurrent {@code resume}s would each fold the log, start the same nodes and promote over each
 * other. Read-only commands never take it. The OS releases the lock if the holder dies, so a crash never leaves a
 * run locked.
 */
public final class RunLock implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(RunLock.class);
    private final FileChannel channel;
    private final FileLock lock;

    private RunLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Takes the lock without waiting.
     *
     * @throws IllegalStateException if another process (or another open orchestrator in this one) holds it
     */
    public static RunLock acquire(Path runDir) {
        Path file = runDir.resolve("run.lock");
        FileChannel channel = null;
        try {
            Files.createDirectories(runDir);
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            FileLock lock = tryLock(channel);
            if (lock == null) {
                String holder = Files.readString(file, StandardCharsets.UTF_8).strip();
                channel.close();
                throw new IllegalStateException("run " + runDir.getFileName() + " is being changed by another process"
                        + (holder.isEmpty() ? "" : " (" + holder + ")") + "; wait for it to finish or pause");
            }
            channel.truncate(0);
            channel.write(ByteBuffer.wrap(("pid " + ProcessHandle.current().pid()).getBytes(StandardCharsets.UTF_8)));
            return new RunLock(channel, lock);
        } catch (IOException exception) {
            closeQuietly(channel);
            throw new UncheckedIOException("cannot lock run directory " + runDir, exception);
        }
    }

    /** Releases the lock (closing its channel releases it). */
    @Override
    public void close() {
        if (lock.isValid()) {
            closeQuietly(channel);
        }
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException _) {
            return null;
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            log.debug("closing the run lock file failed; the OS releases the lock on exit", exception);
        }
    }
}
