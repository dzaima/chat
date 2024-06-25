package chat.networkLog;

import chat.*;
import chat.ui.*;
import chat.utils.UnreadInfo;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.utils.*;
import libMx.*;

import java.lang.reflect.Array;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

public class NetworkLog extends BasicNetworkView {
  private static class TodoEntry { Instant w; Utils.LoggableRequest rq; String type; Object o; }
  
  public static final Deque<RequestInfo> list = new ArrayDeque<>();
  public static final HashMap<Utils.LoggableRequest, RequestInfo> map = new HashMap<>();
  
  public static boolean detailed;
  public final HashMap<RequestInfo, StatusMessage> statusMessages = new HashMap<>();
  
  public final ChatMain m;
  public final ChatUser user;
  public final Chatroom room;
  
  public NetworkLog(ChatMain m) {
    super(m);
    this.m = m;
    user = new ChatUser(m) {
      public Vec<? extends Chatroom> rooms() { return Vec.of(room); }
      public void saveRooms() { }
      public void tick() { }
      public void close() { }
      public String id() { return "network-log"; }
      public JSON.Obj data() { return new JSON.Obj(); }
      public void openLink(String url, Extras.LinkType type, byte[] data) { }
      public void loadImg(String url, Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, Supplier<Boolean> stillNeeded) { }
    };
    room = new Chatroom(user) {
      public void muteStateChanged() { }
      public LiveView mainView() { throw new RuntimeException("NetworkLog mainVew"); }
      public ChatEvent find(String id) { return null; } // TODO?
      public UnreadInfo unreadInfo() { return UnreadInfo.NONE; }
      public Username getUsername(String uid, boolean nullIfUnknown) { return new Username(uid, Promise.resolved(uid)); }
      public void cfgUpdated() { }
      public String asCodeblock(String s) { return null; }
      public URLRes parseURL(String src) { return new URLRes(src, true); }
      public void retryOnFullUserList(Runnable then) { }
      public Vec<UserRes> autocompleteUsers(String prefix) { return new Vec<>(); }
      public void viewProfile(String uid) { }
      public RoomListNode.ExternalDirInfo asDir() { return null; }
      public void viewRoomInfo() { }
      public ChatUser user() { return user; }
      public Pair<Boolean, Integer> highlight(String s) { return new Pair<>(false, 0); }
      public void delete(ChatEvent m) { }
      public void userMenu(Click c, int x, int y, String uid) { }
    };
  }
  
  public static void open(ChatMain m) {
    m.toViewDirect(new NetworkLog(m));
  }
  
  public static Runnable start(ChatMain m, boolean detailed0, int logMinutes) {
    NetworkLog.detailed = detailed0;
    
    ConcurrentLinkedQueue<TodoEntry> todo = new ConcurrentLinkedQueue<>();
    Utils.requestLogger = (rq, type, o) -> {
      Instant now = Instant.now();
      if ("new".equals(type)) m.insertNetworkDelay();
      TodoEntry e = new TodoEntry();
      e.w = now;
      e.rq = rq;
      e.type = type;
      e.o = o;
      todo.add(e);
    };
    
    return () -> {
      NetworkLog lv = m.view instanceof NetworkLog? (NetworkLog) m.view : null;
      
      TodoEntry e;
      while (true) {
        e = todo.poll();
        if (e==null) break;
        
        if (e.type.equals("new")) {
          RequestInfo ri = new RequestInfo(e.w, (MxServer) e.o, e.rq);
          list.addLast(ri);
          map.put(ri.rq, ri);
          if (lv!=null) lv.addRI(ri);
        } else {
          RequestInfo ri = map.get(e.rq);
          if (ri==null) { Log.info("network-log", "unknown request updated"); return; }
          switch (e.type) {
            case "result": ri.status = RequestInfo.Status.DONE; break;
            case "error": ri.status = RequestInfo.Status.ERROR; break;
            case "retry":  ri.status = RequestInfo.Status.RETRYING; break;
            case "cancel": ri.status = RequestInfo.Status.CANCELED; break;
          }
          if (detailed) {
            Event ev = new Event(e.w, e.type, e.o);
            ri.events.add(ev);
            if (m.view instanceof StatusMessage.EventView) {
              StatusMessage.EventView v = (StatusMessage.EventView) m.view;
              if (v.ri == ri) v.addEvent(ev);
            }
          }
          
          if (lv!=null) {
            StatusMessage msg = lv.statusMessages.get(ri);
            if (msg!=null) msg.updateBody(true);
          }
        }
      }
      
      if (!(m.view instanceof BasicNetworkView) && logMinutes!=0) {
        Instant now = Instant.now();
        while (!list.isEmpty()) {
          if (list.getFirst().start.until(now, ChronoUnit.MINUTES) <= logMinutes) break;
          map.remove(list.removeFirst().rq);
        }
      }
    };
  }
  
  public Chatroom room() { return room; }
  
  private final Vec<ChatEvent> visEvents = new Vec<>();
  private void addRI(RequestInfo ri) {
    m.addMessage(visEvents.add(statusMessages.computeIfAbsent(ri, s -> new StatusMessage(this, s))), true);
  }
  public void show() {
    for (RequestInfo ri : list) addRI(ri);
    m.updateCurrentViewTitle();
  }
  public void hide() {
    for (ChatEvent c : visEvents) if (c.visible) c.hide();
  }
  
  public String title() { return "Network log"; }
  
  private static final AtomicLong idCtr = new AtomicLong();
  public static class RequestInfo {
    public final long id = idCtr.incrementAndGet();
    public final Instant start;
    public final MxServer s;
    public final Utils.LoggableRequest rq;
    public enum Status { RUNNING, RETRYING, CANCELED, DONE, ERROR }
    public Status status = Status.RUNNING;
    public final Vec<Event> events = new Vec<>();
    
    public RequestInfo(Instant start, MxServer s, Utils.LoggableRequest rq) {
      this.s = s;
      this.rq = rq;
      this.start = start;
    }
  }
  public static class Event {
    public final long id = idCtr.incrementAndGet();
    public final Instant when;
    public final String type;
    public final Object obj;
    
    public Event(Instant when, String type, Object obj) {
      this.when = when;
      this.type = type;
      if (obj!=null && obj.getClass().isArray()) obj = "(" + Array.getLength(obj) + "-element " + obj.getClass().toGenericString() + ")";
      else if (obj instanceof JSON.Val) obj = new CompactJSON((JSON.Val) obj);
      this.obj = obj;
    }
  }
  public static class CompactJSON {
    public final String str;
    public CompactJSON(JSON.Val v) {
      str = v.toString();
    }
    
    public String toString() {
      return JSON.parse(str).toString(2);
    }
  }
  
  public static class CustomRequest extends Utils.LoggableRequest {
    private final String url;
    public CustomRequest(Utils.RequestType type, String url) {
      super(type, null);
      this.url = url;
    }
    public String calcURL() { return url; }
  }
}
