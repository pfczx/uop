
package com.platform.uop.agent.sdk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class CredentialStore {

  private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------");

  private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");

  private final Path credentialsPath;

  public CredentialStore(Path credentialsPath) {
    if (credentialsPath == null) {
      throw new IllegalArgumentException(
          "Credentials path must not be null");
    }

    this.credentialsPath = credentialsPath
        .toAbsolutePath()
        .normalize();
  }

  public synchronized String load() {
    try {
      if (!Files.exists(credentialsPath)) {
        return null;
      }

      if (!Files.isRegularFile(credentialsPath)) {
        throw new IOException(
            "Credentials path is not a regular file");
      }

      String credential = Files.readString(
          credentialsPath,
          StandardCharsets.UTF_8);

      if (credential.isBlank()) {
        return null;
      }

      return credential;

    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to load agent credentials", exception);
    }
  }

  public synchronized void save(String credential) {
    if (credential == null || credential.isBlank()) {
      throw new IllegalArgumentException(
          "Credential must not be null or blank");
    }

    Path parent = credentialsPath.getParent();
    Path temporaryFile = null;

    try {
      Files.createDirectories(parent);
      setPermissions(parent, DIRECTORY_PERMISSIONS);

      temporaryFile = Files.createTempFile(
          parent,
          ".credentials-",
          ".tmp");

      setPermissions(temporaryFile, FILE_PERMISSIONS);

      try (FileChannel channel = FileChannel.open(
          temporaryFile,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING)) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(credential);

        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }

        channel.force(true);
      }

      try {
        Files.move(
            temporaryFile,
            credentialsPath,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        throw new IOException(
            "Atomic credential replacement is not supported",
            exception);
      }

      temporaryFile = null;

    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to save agent credentials", exception);
    } finally {
      if (temporaryFile != null) {
        try {
          Files.deleteIfExists(temporaryFile);
        } catch (IOException ignored) {
          // Preserve the original exception.
        }
      }
    }
  }

  public synchronized void clear() {
    try {
      Files.deleteIfExists(credentialsPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to clear agent credentials", exception);
    }
  }

  private void setPermissions(
      Path path,
      Set<PosixFilePermission> permissions) throws IOException {
    try {
      Files.setPosixFilePermissions(path, permissions);
    } catch (UnsupportedOperationException ignored) {
      // POSIX permissions are unavailable on this filesystem.
    }
  }
}
