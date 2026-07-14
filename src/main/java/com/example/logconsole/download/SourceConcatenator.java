package com.example.logconsole.download;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Writes staged logs in their configured source order, separated by source headers. */
final class SourceConcatenator {
    Path concatenate(List<StagedSource> sources, Path outputDirectory, String fileName, boolean overwrite)
            throws IOException {
        Files.createDirectories(outputDirectory);
        Path target = outputDirectory.resolve(fileName);
        if (Files.exists(target) && !overwrite) throw new IOException("Output already exists: " + target);

        Path temporary = outputDirectory.resolve("." + fileName + ".tmp");
        Files.deleteIfExists(temporary);
        try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
            for (int i = 0; i < sources.size(); i++) {
                if (i > 0) output.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                writeHeader(output, sources.get(i));
                Files.copy(sources.get(i).getPath(), output);
            }
        }
        move(temporary, target, overwrite);
        return target;
    }

    private static void writeHeader(OutputStream output, StagedSource source) throws IOException {
        String header = "===== SOURCE: " + source.getSource().getLabel() + " =====" + System.lineSeparator();
        output.write(header.getBytes(StandardCharsets.UTF_8));
    }

    private static void move(Path from, Path to, boolean overwrite) throws IOException {
        try {
            if (overwrite) Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            if (overwrite) Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(from, to);
        }
    }
}
