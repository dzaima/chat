package chat.utils;

import dzaima.utils.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.function.Supplier;

public class CacheObj {
  public static Path cachePath = Paths.get("cache");
  public static int cacheDays = 5;
  
  public final Path path;
  public CacheObj(Path p) {
    path = p;
  }
  
  static long nextPurge;
  static long minTimeBetweenPurges = 60*60; // every hour
  public static void purgeOldCache() {
    long currTime = System.currentTimeMillis()/1000;
    if (currTime > nextPurge) nextPurge = currTime+minTimeBetweenPurges;
    else return;
    
    if (!Files.isDirectory(cachePath)) return;
    try (DirectoryStream<Path> s = Files.newDirectoryStream(cachePath)) {
      for (Path p : s) {
        Duration d = Duration.between(Files.getLastModifiedTime(p).toInstant(), Instant.now());
        if (d.toDays() > cacheDays) Files.deleteIfExists(p);
      }
    } catch (IOException e) {
      Log.stacktrace("purgeOldCache", e);
    }
  }
  
  public static CacheObj forID(byte[] id) {
    return new CacheObj(cachePath.resolve(Tools.sha256(id)));
  }
  
  public static byte[] compute(String id, Supplier<byte[]> compute, Runnable onCached) {
    return compute(id.getBytes(StandardCharsets.UTF_8), compute, onCached);
  }
  public static byte[] compute(byte[] id, Supplier<byte[]> compute, Runnable onCached) {
    CacheObj v = forID(id);
    
    byte[] p = v.find();
    if (p!=null) {
      onCached.run();
    } else {
      p = compute.get();
      if (p!=null) v.store(p);
    }
    return p;
  }
  
  public byte[] find() {
    byte[] res = null;
    if (Files.exists(path)) {
      try {
        Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
      } catch (IOException e) { Log.warn("cache", "Failed to update cache file last modified time"); }
      try {
        res = Files.readAllBytes(path);
      } catch (IOException e) { Log.warn("cache", "Failed reading cache:"); Log.stacktrace("cache", e); }
    }
    purgeOldCache();
    return res;
  }
  
  public void store(byte[] bytes) {
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, bytes);
    } catch (IOException e) {
      Log.warn("cache", "Failed to write file to cache");
      Log.stacktrace("cache", e);
    }
  }
}
