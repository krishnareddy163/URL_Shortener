package com.example.agentic.core.workspace;

import com.example.agentic.core.agent.WorkspaceView;
import com.example.agentic.core.state.Hashing;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The promoted workspace: the only place engine-accepted work lives. Only the engine writes here, and only
 * through {@link #promote} and {@link #revertAll}.
 *
 * <p>Staging copies are taken under a read lock and promotions under a write lock, so parallel nodes never
 * see a half-promoted tree. Promotion copies only the node's own files and fails if any of them changed
 * since staging. Each promotion stores pre-images so {@link #revertAll} can undo it on invalidation.
 */
public final class Workspace {
    private static final String BEFORE = "before";
    private static final String AFTER = "after";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Map<String, String>>> MANIFEST = new TypeReference<>() { };

    private final Path root;
    private final Path stagingRoot;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong stagingCounter = new AtomicLong();
    private final GitHistory history;

    public Workspace(Path root, Path stagingRoot) {
        this.root = root.toAbsolutePath().normalize();
        this.stagingRoot = stagingRoot.toAbsolutePath().normalize();
        this.history = new GitHistory(this.root);
    }

    public Path root() {
        return root;
    }

    /** Git history of promotions and reverts in this workspace. */
    public GitHistory history() {
        return history;
    }

    /** Copies the promoted tree into a fresh staging directory for one attempt. */
    public Staging stage(String label) throws IOException {
        Path directory = stagingRoot.resolve(label + "-" + System.nanoTime() + "-" + stagingCounter.incrementAndGet());
        lock.readLock().lock();
        try {
            FileTrees.copyTree(root, directory);
        } finally {
            lock.readLock().unlock();
        }
        return new Staging(directory);
    }

    /** Removes staging leftovers from a crashed process. */
    public void clearStaging() throws IOException {
        FileTrees.deleteTree(stagingRoot);
    }

    /**
     * Copies {@code baseHashes.keySet()} from {@code stagedTree} into the workspace.
     *
     * @param baseHashes each file's hash at staging time; promotion aborts if the workspace differs
     * @param recordDir  where pre-images and the manifest are written for {@link #revertAll}
     * @return promoted paths
     */
    public List<String> promote(Path stagedTree, Map<String, String> baseHashes, Path recordDir)
            throws IOException, PromotionConflictException {
        lock.writeLock().lock();
        try {
            for (Map.Entry<String, String> entry : baseHashes.entrySet()) {
                String current = FileTrees.fileHash(root.resolve(entry.getKey()));
                if (!Hashing.sameHash(current, entry.getValue())) {
                    throw new PromotionConflictException("workspace file changed since staging: " + entry.getKey());
                }
            }
            FileTrees.deleteTree(recordDir);
            Map<String, Map<String, String>> manifest = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : baseHashes.entrySet()) {
                String relative = entry.getKey();
                Path current = root.resolve(relative);
                if (Files.isRegularFile(current)) {
                    Path preImage = recordDir.resolve(BEFORE).resolve(relative);
                    Files.createDirectories(FileTrees.parentOf(preImage));
                    Files.copy(current, preImage, StandardCopyOption.REPLACE_EXISTING);
                }
                Path source = stagedTree.resolve(relative);
                Files.createDirectories(FileTrees.parentOf(current));
                Files.copy(source, current, StandardCopyOption.REPLACE_EXISTING);
                manifest.put(relative, Map.of(BEFORE, entry.getValue(), AFTER, FileTrees.fileHash(current)));
            }
            Files.createDirectories(recordDir);
            JSON.writerWithDefaultPrettyPrinter().writeValue(recordDir.resolve("manifest.json").toFile(), manifest);
            return new ArrayList<>(baseHashes.keySet());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Undoes several promotions atomically with respect to conflicts. {@code recordDirs} must be ordered newest
     * promotion first. The whole sequence is validated against a simulated view of the workspace before any file
     * changes, so a file modified by something other than these promotions aborts the revert with the workspace
     * untouched.
     *
     * @return reverted paths per record directory, in the given order
     */
    public Map<Path, List<String>> revertAll(List<Path> recordDirs) throws IOException, PromotionConflictException {
        lock.writeLock().lock();
        try {
            Map<Path, Map<String, Map<String, String>>> manifests = new LinkedHashMap<>();
            Map<String, String> simulated = new HashMap<>();
            for (Path recordDir : recordDirs) {
                Map<String, Map<String, String>> manifest = readManifest(recordDir);
                for (Map.Entry<String, Map<String, String>> entry : manifest.entrySet()) {
                    String path = entry.getKey();
                    String current = simulated.containsKey(path) ? simulated.get(path) : FileTrees.fileHash(root.resolve(path));
                    if (!Hashing.sameHash(current, entry.getValue().get(AFTER))) {
                        throw new PromotionConflictException("cannot revert " + path + ": it was modified after promotion");
                    }
                    simulated.put(path, entry.getValue().get(BEFORE));
                }
                manifests.put(recordDir, manifest);
            }
            Map<Path, List<String>> reverted = new LinkedHashMap<>();
            for (Map.Entry<Path, Map<String, Map<String, String>>> manifest : manifests.entrySet()) {
                restore(manifest.getKey(), manifest.getValue());
                reverted.put(manifest.getKey(), new ArrayList<>(manifest.getValue().keySet()));
            }
            return reverted;
        } finally {
            lock.writeLock().unlock();
        }
    }


    private static Map<String, Map<String, String>> readManifest(Path recordDir) throws IOException {
        Path manifestFile = recordDir.resolve("manifest.json");
        return Files.isRegularFile(manifestFile) ? JSON.readValue(manifestFile.toFile(), MANIFEST) : Map.of();
    }

    private void restore(Path recordDir, Map<String, Map<String, String>> manifest) throws IOException {
        for (Map.Entry<String, Map<String, String>> entry : manifest.entrySet()) {
            Path target = root.resolve(entry.getKey());
            if (FileTrees.ABSENT.equals(entry.getValue().get(BEFORE))) {
                Files.deleteIfExists(target);
            } else {
                Files.copy(recordDir.resolve(BEFORE).resolve(entry.getKey()), target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public String treeHash() throws IOException {
        lock.readLock().lock();
        try {
            return FileTrees.treeHash(root);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Read-only view for agents. */
    public WorkspaceView view() {
        return new WorkspaceView() {
            @Override
            public List<String> listFiles() {
                lock.readLock().lock();
                try {
                    return FileTrees.listFiles(root);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                } finally {
                    lock.readLock().unlock();
                }
            }

            @Override
            public Optional<String> read(String relativePath) {
                Path target = root.resolve(relativePath).normalize();
                if (!target.startsWith(root) || !Files.isRegularFile(target) || !isInsideRealRoot(target)) {
                    return Optional.empty();
                }
                lock.readLock().lock();
                try {
                    return Optional.ofNullable(FileTrees.readOrNull(target));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                } finally {
                    lock.readLock().unlock();
                }
            }
        };
    }

    /** True if the file's real path (all symlinks resolved) is still inside the workspace. */
    private boolean isInsideRealRoot(Path file) {
        try {
            return file.toRealPath().startsWith(root.toRealPath());
        } catch (IOException _) {
            return false;
        }
    }

    /** Reads the files listed in {@code paths} from a staged tree (used to re-hash retained proposals). */
    public static Map<String, String> readFiles(Path tree, Iterable<String> paths) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        for (String relative : paths) {
            Path file = tree.resolve(relative);
            if (!Files.isRegularFile(file)) {
                throw new IOException("retained file missing: " + relative);
            }
            files.put(relative, Files.readString(file, StandardCharsets.UTF_8));
        }
        return files;
    }
}
