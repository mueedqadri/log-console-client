package com.example.logconsole.download;

import com.example.logconsole.config.AppConfig;
import com.example.logconsole.config.TemplateEngine;
import com.example.logconsole.config.ConfigResolver;
import com.example.logconsole.http.RangeHttpClient;
import com.example.logconsole.model.ExpandedSource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class DownloadService {
    public interface Progress {
        void update(int sourceNumber, int sourceCount, ExpandedSource source, long completed, long total);
    }

    private final AppConfig config;
    private final RangeHttpClient client;

    public DownloadService(AppConfig config, RangeHttpClient client) {
        this.config = config;
        this.client = client;
    }

    public Path download(String applicationId, LocalDate date, List<ExpandedSource> selected, Progress progress)
            throws IOException {
        if (selected == null || selected.isEmpty()) throw new IllegalArgumentException("At least one source must be selected");
        AppConfig.ApplicationConfig application = config.applications.get(applicationId);
        String environment = application.environment;
        AppConfig.DownloadConfig downloadConfig = ConfigResolver.download(config, application);
        Path outputDirectory = Paths.get(downloadConfig.root, safe(applicationId), safe(environment), date.toString()).normalize();
        java.util.Map<String, String> template = new java.util.LinkedHashMap<String, String>();
        template.put("application", application.application == null ? applicationId : application.application);
        template.put("application.id", applicationId);
        template.put("environment", environment);
        template.put("date", date.format(java.time.format.DateTimeFormatter.ofPattern(application.outputDatePattern)));
        String fileName = safeFileName(TemplateEngine.render(application.outputFileTemplate, template));
        Files.createDirectories(outputDirectory);
        Path output = outputDirectory.resolve(fileName);
        Path jobDirectory = Files.createTempDirectory(outputDirectory, "." + fileName + ".download-");
        Path temporary = jobDirectory.resolve(fileName + ".tmp");
        int concurrency = Math.max(1, Math.min(selected.size(), selected.get(0).getConnection().concurrency));
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CompletionService<StagedPart> completion = new ExecutorCompletionService<StagedPart>(executor);
        List<Future<StagedPart>> futures = new ArrayList<Future<StagedPart>>();
        boolean complete = false;
        try {
            for (int i = 0; i < selected.size(); i++) {
                final int sourceNumber = i + 1;
                final ExpandedSource source = selected.get(i);
                final Path part = jobDirectory.resolve(String.format("%04d.part", sourceNumber));
                futures.add(completion.submit(new Callable<StagedPart>() {
                    @Override public StagedPart call() throws Exception {
                        client.downloadTo(source.getUrl(), source.getConnection(), part,
                                new RangeHttpClient.TransferProgress() {
                                    @Override public void update(long completed, long total) {
                                        notifyProgress(progress, sourceNumber, selected.size(), source, completed, total);
                                    }
                                });
                        return new StagedPart(sourceNumber - 1, part);
                    }
                }));
            }
            executor.shutdown();
            List<Path> parts = collect(completion, selected.size());
            await(executor);
            combine(temporary, selected, parts);
            move(temporary, output);
            complete = true;
            return output;
        } catch (IOException e) {
            cancel(futures, executor);
            throw e;
        } catch (RuntimeException e) {
            cancel(futures, executor);
            throw e;
        } finally {
            if (!complete) {
                cancel(futures, executor);
                Files.deleteIfExists(temporary);
            }
            deleteTree(jobDirectory);
        }
    }

    private static List<Path> collect(CompletionService<StagedPart> completion, int count) throws IOException {
        Path[] ordered = new Path[count];
        for (int i = 0; i < count; i++) {
            try {
                StagedPart part = completion.take().get();
                ordered[part.index] = part.path;
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted", e);
            } catch (CancellationException e) {
                throw new IOException("Download cancelled", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) throw (IOException) cause;
                throw new IOException("Download worker failed", cause);
            }
        }
        List<Path> result = new ArrayList<Path>();
        for (Path path : ordered) result.add(path);
        return result;
    }

    private static void combine(Path temporary, List<ExpandedSource> sources, List<Path> parts) throws IOException {
        try (OutputStream writer = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
            for (int i = 0; i < sources.size(); i++) {
                if (i > 0) writer.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                writer.write(("===== SOURCE: " + sources.get(i).getLabel() + " =====" + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8));
                Files.copy(parts.get(i), writer);
            }
        }
    }

    private static void cancel(List<? extends Future<?>> futures, ExecutorService executor) throws IOException {
        for (Future<?> future : futures) if (!future.isDone()) future.cancel(true);
        executor.shutdownNow();
        await(executor);
    }

    private static void await(ExecutorService executor) throws IOException {
        try {
            while (!executor.awaitTermination(1, TimeUnit.DAYS)) { }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while closing download workers", e);
        }
    }

    private static void notifyProgress(Progress progress, int sourceNumber, int sourceCount, ExpandedSource source,
                                       long completed, long total) {
        if (progress != null) progress.update(sourceNumber, sourceCount, source, completed, total);
    }

    private static String safe(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized)) throw new IllegalArgumentException("Unsafe path segment: " + value);
        return sanitized;
    }

    private static String safeFileName(String name) {
        if (!name.endsWith(".log") || !Paths.get(name).getFileName().toString().equals(name)
                || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Unsafe output filename: " + name);
        }
        return name;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class StagedPart {
        final int index;
        final Path path;

        StagedPart(int index, Path path) { this.index = index; this.path = path; }
    }
}
