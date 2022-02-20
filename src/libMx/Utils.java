package libMx;

import dzaima.utils.JSON;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Utils {
  public static final Object qnull = null;
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
  
  public static String toMx(String s) {
    return s.replace("\\","\\\\").replace("_","\\_").replace("*","\\*").replace("[","\\[").replace("]","\\]");
  }
  
  public static String post(String path, byte[] data) {
    return postPut("POST", path, data);
  }
  public static String put(String path, byte[] data) {
    return postPut("PUT", path, data);
  }
  public static String postPut(String method, String path, byte[] data) {
    try {
      URL u = new URL(path);
      HttpURLConnection c = (HttpURLConnection) u.openConnection();
      c.setConnectTimeout(globalTimeout);
      c.setReadTimeout(globalTimeout);
      c.setRequestMethod(method);
      c.setUseCaches(false);
      c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      c.setRequestProperty("Content-Length", Integer.toString(data.length));
      c.setRequestProperty("Content-Language", "en-US");
      
      c.setDoOutput(true);
      OutputStream os = c.getOutputStream();
      os.write(data);
      os.close();
      
      try (InputStream is = c.getResponseCode()>=400? c.getErrorStream() : c.getInputStream()) {
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
      e.printStackTrace();
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
        e.printStackTrace();
      }
    });
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
  
  public static Thread thread(Runnable o) {
    Thread th = new Thread(o);
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
}
