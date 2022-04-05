package chat;

import dzaima.utils.Tools;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;

public class CacheObj {
  public static Path cachePath = Paths.get("cache");
  public static int cacheDays = 5;
  
  public final Path path;
  public CacheObj(Path p) {
    path = p;
  }
  
  public static void purgeOldCache() {
    if (!Files.isDirectory(cachePath)) return;
    try (DirectoryStream<Path> s = Files.newDirectoryStream(cachePath)) {
      for (Path p : s) {
        Duration d = Duration.between(Files.getLastModifiedTime(p).toInstant(), Instant.now());
        if (d.toDays() > cacheDays) Files.deleteIfExists(p);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  public static CacheObj forID(byte[] id) {
    return new CacheObj(cachePath.resolve(Tools.sha256(id)));
  }
  
  public byte[] find() {
    byte[] res = null;
    if (Files.exists(path)) {
      try {
        Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
      } catch (IOException e) { ChatMain.warn("Failed to update cache file last modified time"); }
      try {
        res = Files.readAllBytes(path);
      } catch (IOException e) { System.out.println("Failed reading cache:"); e.printStackTrace(); }
    }
    purgeOldCache();
    return res;
  }
  
  public void store(byte[] bytes) {
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, bytes);
    } catch (IOException e) {
      ChatMain.warn("Failed to write file to cache");
      e.printStackTrace();
    }
  }
}
