package chat.mx;

import chat.CacheObj;
import chat.networkLog.NetworkLog;
import dzaima.utils.*;
import libMx.Utils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class MediaThread {
  public static final int MAX_CONCURRENT = 5;
  private final AtomicInteger active = new AtomicInteger();
  
  private final Vec<Request> todo = new Vec<>(); // only touched from main
  private final ConcurrentHashMap<String, Request> map = new ConcurrentHashMap<>(); // inserted into from main, deleted from by launched
  
  // invoke from main thread
  public void request(String url, Consumer<byte[]> onDone, Supplier<Boolean> stillNeeded) {
    Request r = map.computeIfAbsent(url, url1 -> todo.add(new Request(url1))); // add is fine before r.users.add because it's only on main thread
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
          Utils.LoggableRequest rq = new NetworkLog.CustomRequest(Utils.RequestType.GET, r.url);
          Utils.requestLogger.got(rq, "new", null);
          byte[] res;
          try {
            res = CacheObj.compute(r.url, () -> {
              MxChatUser.logGet("Load image", r.url);
              Utils.requestLogger.got(rq, "not in cache, requesting", null);
              return Tools.get(r.url, true);
            });
          } catch (Throwable t) {
            res = null;
            Log.warn("media", "Failed to load "+r.url);
          }
          Utils.requestLogger.got(rq, "result", res);
          map.remove(r.url); // result is already in cache, so it's fine; we need r.users to stop being modified
          for (Pair<Consumer<byte[]>, Supplier<Boolean>> u : r.users) u.a.accept(res);
          
          active.decrementAndGet();
        });
      }
    }
  }
  
  private static class Request {
    final String url;
    final ConcurrentLinkedQueue<Pair<Consumer<byte[]>, Supplier<Boolean>>> users; // modified on main, read by launched
    private Request(String url) {
      this.url = url;
      this.users = new ConcurrentLinkedQueue<>();
    }
  }
}
