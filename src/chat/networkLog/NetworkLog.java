package chat.networkLog;

import chat.*;
import chat.ui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.utils.*;
import libMx.MxServer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

public class NetworkLog extends BasicNetworkView {
  private static class TodoEntry { Instant w; MxServer.RunnableRequest rq; String type; Object o; }
  
  public static final Deque<RequestInfo> list = new ArrayDeque<>();
  public static final HashMap<MxServer.RunnableRequest, RequestInfo> map = new HashMap<>();
  
  public static boolean detailed;
  public final HashMap<RequestInfo, StatusMessage> statusMessages = new HashMap<>();
  
  public final ChatMain m;
  public final ChatUser user;
  public final Chatroom room;
  
  public NetworkLog(ChatMain m) {
    this.m = m;
    user = new ChatUser(m) {
      public Vec<Chatroom> rooms() { return Vec.of(room); }
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
      public String getUsername(String uid, boolean nullIfUnknown) { return uid; }
      public void cfgUpdated() { }
      public URLRes parseURL(String src) { return new URLRes(src, true); }
      public void retryOnFullUserList(Runnable then) { }
      public Vec<UserRes> autocompleteUsers(String prefix) { return new Vec<>(); }
      public void viewProfile(String uid) { }
      public RoomListNode.ExternalDirInfo asDir() { return null; }
      public void viewRoomInfo() { }
      public ChatUser user() { return user; }
      public void readAll() { }
      public void older() { }
      public Pair<Boolean, Integer> highlight(String s) { return new Pair<>(false, 0); }
      public void delete(ChatEvent m) { }
      public void pinged() { }
      public void userMenu(Click c, int x, int y, String uid) { }
    };
  }
  
  public static void open(ChatMain m) {
    m.toViewDirect(new NetworkLog(m));
  }
  
  public static Runnable start(ChatMain m, boolean detailed0) {
    NetworkLog.detailed = detailed0;
    
    ConcurrentLinkedQueue<TodoEntry> todo = new ConcurrentLinkedQueue<>();
    MxServer.requestLogger = (rq, type, o) -> {
      Instant now = Instant.now();
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
      
      if (!detailed && !(m.view instanceof BasicNetworkView)) {
        Instant now = Instant.now();
        while (!list.isEmpty()) {
          if (list.getFirst().start.until(now, ChronoUnit.MINUTES) <= 10) break;
          map.remove(list.removeFirst().rq);
        }
      }
    };
  }
  
  public Chatroom room() { return room; }
  public void openViewTick() { }
  public void show() {
    for (RequestInfo ri : list) addRI(ri);
    m.updateCurrentViewTitle();
  }
  
  private void addRI(RequestInfo ri) {
    m.addMessage(statusMessages.computeIfAbsent(ri, s -> new StatusMessage(this, s)), true);
  }
  
  public void hide() {
    for (StatusMessage c : statusMessages.values()) c.hide();
  }
  public String title() { return "Network log"; }
  public final boolean key(Key key, int scancode, KeyAction a) { return false; }
  
  private static final AtomicLong idCtr = new AtomicLong();
  public static class RequestInfo {
    public final long id = idCtr.incrementAndGet();
    public final Instant start;
    public final MxServer s;
    public final MxServer.RunnableRequest rq;
    public enum Status { RUNNING, RETRYING, CANCELED, DONE }
    public Status status = Status.RUNNING;
    public final Vec<Event> events = new Vec<>();
    
    public RequestInfo(Instant start, MxServer s, MxServer.RunnableRequest rq) {
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
      this.obj = obj instanceof JSON.Val? new CompactJSON((JSON.Val) obj) : obj;
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
}
