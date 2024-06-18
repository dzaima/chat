package chat.networkLog;

import chat.*;
import chat.ui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.utils.*;
import libMx.MxServer;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

public class NetworkLog extends View {
  public static ConcurrentHashMap<MxServer.RunnableRequest, RequestStatus> requestMap = new ConcurrentHashMap<>();
  public static ConcurrentLinkedQueue<RequestStatus> requestList = new ConcurrentLinkedQueue<>();
  public static boolean detailed;
  
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
      public String getUsername(String uid) { return uid; }
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
  
  public static void start(boolean detailed) {
    NetworkLog.detailed = detailed;
    MxServer.requestLogger = (s, rq) -> {
      RequestStatus st = new RequestStatus(Instant.now(), s, rq);
      requestMap.put(rq, st);
      requestList.add(st);
    };
    MxServer.requestStatusLogger = (rq, type, o) -> {
      Instant when = Instant.now();
      RequestStatus st = requestMap.get(rq);
      if (st==null) { Log.warn("", "unknown request?"); return; }
      switch (type) {
        case "result": st.status = RequestStatus.Status.DONE; break;
        case "retry":  st.status = RequestStatus.Status.RETRYING; break;
        case "cancel": st.status = RequestStatus.Status.CANCELED; break;
      }
      if (detailed) st.events.add(new Event(when, type, o));
    };
  }
  
  public Chatroom room() { return room; }
  
  public void openViewTick() {
    
  }
  public boolean open;
  public void show() {
    open = true;
    for (RequestStatus s : requestList) m.addMessage(new StatusMessage(this, s), true);
    m.updateCurrentViewTitle();
  }
  public void hide() {
    open = false;
  }
  public String title() { return "Network log"; }
  public boolean key(Key key, int scancode, KeyAction a) { return false; }
  public boolean typed(int codepoint) { return false; }
  public String asCodeblock(String s) { return s; }
  public LiveView baseLiveView() { return null; }
  public boolean contains(ChatEvent ev) { return false; }
  
  private static final AtomicLong idCtr = new AtomicLong();
  public static class RequestStatus {
    public final long id = idCtr.incrementAndGet();
    public final Instant start;
    public final MxServer s;
    public final MxServer.RunnableRequest rq;
    public enum Status { RUNNING, RETRYING, CANCELED, DONE }
    public Status status = Status.RUNNING;
    public final Vec<Event> events = new Vec<>(); 
    
    public RequestStatus(Instant start, MxServer s, MxServer.RunnableRequest rq) {
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
      this.obj = obj;
    }
  }
}
