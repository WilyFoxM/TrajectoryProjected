package ru.wilyfox.trajectory.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Replaces a saved file only after its complete UTF-8 contents have been written. */
public final class AtomicFileWriter {
  private AtomicFileWriter() {}

  public static void write(Path target, WriterAction action) throws IOException {
    Path destination = target.toAbsolutePath();
    Files.createDirectories(destination.getParent());
    Path temporary =
        Files.createTempFile(destination.getParent(), destination.getFileName() + ".", ".tmp");
    try {
      try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        action.write(writer);
      }
      try {
        Files.move(
            temporary,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  @FunctionalInterface
  public interface WriterAction {
    void write(Writer writer) throws IOException;
  }
}
