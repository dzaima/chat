package libMx;

import dzaima.utils.JSON;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;

public class Utils {
  public static int globalTimeout = 1000*60*3; // timeout of all requests; default is 3 minutes
  
  public static String toJSON(String str) {
    return JSON.quote(str);
  }
  
  public static String toHTML(String s) {
    return toHTML(s, true);
  }
  public static String toHTML(String s, boolean nlToBr) {
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c=='"' || c=='\'' || c=='&' || c=='<' || c=='>') {
        b.append("&#").append((int) c).append(';');
      } else if (nlToBr && c=='\n') {
        b.append("<br>");
      } else b.append(c);
    }
    return b.toString();
  }
  
  public static String toURI(String s) {
    try {
      return URLEncoder.encode(s, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
  
  public static String post(String path, byte[] data) {
    return postPut("POST", path, data);
  }
  public static String put(String path, byte[] data) {
    return postPut("PUT", path, data);
  }
  public static String postPut(String method, String path, byte[] data) {
    return postPut(method, path, data, "application/x-www-form-urlencoded");
  }
  public static String postPut(String method, String path, byte[] data, String contentType) {
    try {
      URL u = new URL(path);
      HttpURLConnection c = (HttpURLConnection) u.openConnection();
      c.setConnectTimeout(globalTimeout);
      c.setReadTimeout(globalTimeout);
      c.setRequestMethod(method);
      c.setUseCaches(false);
      c.setRequestProperty("Content-Type", contentType);
      c.setRequestProperty("Content-Length", Integer.toString(data.length));
      c.setRequestProperty("Content-Language", "en-US");
      
      c.setDoOutput(true);
      OutputStream os = c.getOutputStream();
      os.write(data);
      os.close();
      
      try (InputStream is = c.getResponseCode()>=400? c.getErrorStream() : c.getInputStream()) {
        if (is==null) throw new RuntimeException("Failed to get result stream");
        return new String(readAll(is), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public static String get(String path) {
    try {
      URL u = new URL(path);
      HttpURLConnection c = (HttpURLConnection) u.openConnection();
      c.setConnectTimeout(globalTimeout);
      c.setReadTimeout(globalTimeout);
      c.setRequestMethod("GET");
      c.setUseCaches(false);
      
      try (InputStream is = c.getResponseCode()>=400? c.getErrorStream() : c.getInputStream()) {
        return new String(readAll(is), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  public static byte[] getB(String path) {
    try {
      URL u = new URL(path);
      HttpURLConnection c = (HttpURLConnection) u.openConnection();
      c.setConnectTimeout(globalTimeout);
      c.setRequestMethod("GET");
      c.setUseCaches(false);
      
      try (InputStream is = c.getResponseCode()>=400? c.getErrorStream() : c.getInputStream()) {
        return readAll(is);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public static void sleep(int ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
  
  public static void pipe(InputStream i, PrintStream o) {
    Scanner s = new Scanner(i);
    thread(() -> {
      try {
        while(s.hasNextLine()) {
          o.println(s.nextLine());
          o.flush();
        }
      } catch (Exception e) {
        warnStacktrace(e);
      }
    }, false);
  }
  
  
  public static byte[] readAll(InputStream s) throws IOException {
    byte[] b = new byte[1024];
    int i = 0, am;
    while ((am = s.read(b, i, b.length-i))!=-1) {
      i+= am;
      if (i==b.length) b = Arrays.copyOf(b, b.length*2);
    }
    return Arrays.copyOf(b, i);
  }
  
  public static Thread thread(Runnable o, boolean daemon) {
    Thread th = new Thread(o);
    th.setDaemon(daemon);
    th.start();
    return th;
  }
  
  public static void write(Path p, String s) {
    try {
      Files.write(p, s.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  // these must be thread-safe!
  public static boolean enableLogging = true;
  public static BiConsumer<String, String> logFn  = (id, s) -> System.out.println("["+LocalDateTime.now()+" "+id+"] "+s);
  public static BiConsumer<String, String> warnFn = (id, s) -> System.err.println("["+LocalDateTime.now()+" !!] "+s);
  public static RequestStatus requestLogger = (rq, type, o) -> {};
  
  public static void log(String id, String s) {
    if (enableLogging) logFn.accept(id, s);
  }
  public static void warn(String s) {
    warnFn.accept("mx itf", s);
  }
  public static void warnStacktrace(Throwable t) {
    StringWriter w = new StringWriter();
    t.printStackTrace(new PrintWriter(w));
    warnFn.accept("mx itf", w.toString());
  }
  
  public enum RequestType { POST, GET, PUT }
  
  @FunctionalInterface public interface RequestStatus { void got(LoggableRequest rq, String type, Object o); }
  
  public static abstract class LoggableRequest {
    public final RequestType t;
    public final String ct;
    public LoggableRequest(RequestType t, String ct) {
      this.t = t;
      this.ct = ct;
    }
    public abstract String calcURL();
  }
}
