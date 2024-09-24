package chat.mx;

import chat.networkLog.NetworkLog;
import chat.utils.CacheObj;
import dzaima.utils.*;
import libMx.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class MediaThread {
  public static final int MAX_CONCURRENT = 5;
  private final AtomicInteger active = new AtomicInteger();
  
  private final Vec<Request> todo = new Vec<>(); // only touched from main
  private final ConcurrentHashMap<String, Request> map = new ConcurrentHashMap<>(); // inserted into from main, deleted from by launched
  
  // invoke from main thread
  public void request(MediaRequest rq, Consumer<byte[]> onDone, Supplier<Boolean> stillNeeded) { // onDone called on spawned thread
    Request r = map.computeIfAbsent(rq.key(), u -> todo.add(new Request(rq))); // add is fine before r.users.add because it's only on main thread
    r.users.add(new Pair<>(onDone, stillNeeded));
  }
  
  
  
  // invoke from main thread
  public void tick() {
    while (active.get()<MAX_CONCURRENT && todo.sz>0) {
      Request r = todo.pop();
      boolean needed = false;
      for (Pair<Consumer<byte[]>, Supplier<Boolean>> u : r.users) {
        if (u.b.get()) { needed = true; break; }
      }
      
      if (needed) {
        active.incrementAndGet();
        Tools.thread(() -> {
          String logLink = r.rq.logLink();
          Utils.LoggableRequest rq = new NetworkLog.CustomRequest(null, "cache-get "+logLink);
          Utils.requestLogger.got(rq, "new", null);
          byte[] res;
          try {
            res = CacheObj.compute(r.rq.key(), () -> {
              MxChatUser.logGet("Load media", logLink);
              Utils.requestLogger.got(rq, "not in cache, requesting", null);
              Pair<byte[], Boolean> got = r.rq.requestHere();
              if (!got.b) Utils.requestLogger.got(rq, "error", new String(got.a, StandardCharsets.UTF_8));
              return got.b? got.a : null;
            }, () -> Utils.requestLogger.got(rq, "received result from cache", null));
          } catch (Throwable t) {
            res = null;
            Log.warn("media", "Failed to load "+logLink);
            Utils.requestLogger.got(rq, "error", t.getMessage());
          }
          if (res!=null) Utils.requestLogger.got(rq, "result", res);
          map.remove(r.rq.key()); // result is already in cache, so it's fine; we need r.users to stop being modified
          for (Pair<Consumer<byte[]>, Supplier<Boolean>> u : r.users) u.a.accept(res);
          
          active.decrementAndGet();
        });
      }
    }
  }
  
  private static class Request {
    final MediaRequest rq;
    final ConcurrentLinkedQueue<Pair<Consumer<byte[]>, Supplier<Boolean>>> users; // modified on main, read by launched
    private Request(MediaRequest rq) {
      this.rq = rq;
      this.users = new ConcurrentLinkedQueue<>();
    }
  }
  
  public abstract static class MediaRequest {
    public abstract String key();
    public abstract String logLink();
    public abstract Pair<byte[], Boolean> requestHere(); // bool is whether result is good
    
    public static class FromMxRequest extends MediaRequest {
      public final MxServer.Request rq;
      
      public FromMxRequest(MxServer.Request rq) {
        this.rq = rq;
      }
      
      public String logLink() { return rq.calcCurrentPath(); }
      public String key() { return "mx;"+rq.calcCurrentPath(); }
      
      public Pair<byte[], Boolean> requestHere() {
        Utils.RequestRes r = rq.get().runBytesOpt();
        return new Pair<>(r.bytes, r.ok());
      }
    }
    public static class FromURL extends MediaRequest {
      public final String url;
      public FromURL(String url) { this.url = url; }
      
      public String logLink() { return url; }
      public String key() { return "raw;"+url; }
      
      public Pair<byte[], Boolean> requestHere() {
        Utils.RequestRes r = Utils.get(new Utils.RequestParams(null), url);
        return new Pair<>(r.bytes, r.ok());
      }
    }
  }
}
