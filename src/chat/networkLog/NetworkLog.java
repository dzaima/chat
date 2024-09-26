package chat.networkLog;

import chat.*;
import chat.mx.MxChatUser;
import chat.ui.*;
import chat.utils.UnreadInfo;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.utils.*;
import libMx.*;

import java.io.*;
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
  public final MxChatUser mxUser;
  public final ChatUser user;
  public final Chatroom room;
  
  public NetworkLog(MxChatUser mxUser) {
    super(mxUser.m);
    this.mxUser = mxUser;
    this.m = mxUser.m;
    user = new ChatUser(m) {
      public Vec<? extends Chatroom> rooms() { return Vec.of(room); }
      public void saveRooms() { }
      public void tick() { }
      public void close() { }
      public String id() { return "network-log"; }
      public JSON.Obj data() { return new JSON.Obj(); }
      public URIInfo parseURI(String src, JSON.Obj info) { return null; }
      public void loadImg(URIInfo info, boolean acceptThumbnail, Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, Supplier<Boolean> stillNeeded) { }
      public void openLink(String url, Extras.LinkInfo info) { }
    };
    room = new Chatroom(user) {
      public void muteStateChanged() { }
      public LiveView mainView() { throw new RuntimeException("NetworkLog mainVew"); }
      public ChatEvent find(String id) { return null; } // TODO?
      public UnreadInfo unreadInfo() { return UnreadInfo.NONE; }
      public Username getUsername(String uid, boolean nullIfUnknown) { return new Username(uid, Promise.resolved(uid)); }
      public void cfgUpdated() { }
      public String asCodeblock(String s) { return null; }
      public void retryOnFullUserList(Runnable then) { }
      public Vec<UserRes> autocompleteUsers(String prefix) { return new Vec<>(); }
      public void viewProfile(String uid) { }
      public RoomListNode.ExternalDirInfo asDir() { return null; }
      public void viewRoomInfo() { }
      public ChatUser user() { return user; }
      public Pair<Boolean, Integer> highlight(String s) { return new Pair<>(false, Chatroom.commandPrefix(Chatroom.splitCommand(s), mxUser.commands)); }
      public void delete(ChatEvent m) { }
      public void userMenu(Click c, int x, int y, String uid) { }
    };
    createInput();
  }
  
  public Node inputPlaceContent() {
    return input;
  }
  public boolean post(String raw, String replyTo) {
    String[] cmd = Chatroom.splitCommand(raw);
    if (cmd.length==1) return false;
    Command c = Chatroom.findCommand(cmd, mxUser.commands);
    if (c==null) return false;
    c.run(cmd[1]);
    return true;
  }
  public Vec<Command> allCommands() {
    return mxUser.commands;
  }
  
  public static void open(MxChatUser u) {
    NetworkLog v = new NetworkLog(u);
    u.m.toViewDirect(v);
    u.m.inputPlace.replace(0, v.inputPlaceContent()); // TODO avoid needing this somehow
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
          if (detailed || m.networkViewOpen()) {
            Event ev = new Event(e.w, e.type, e.o);
            ri.events.add(ev);
            if (m.view instanceof StatusMessage.EventView) {
              StatusMessage.EventView v = (StatusMessage.EventView) m.view;
              if (v.ri == ri) v.addEvent(ev);
            }
          }
          
          if (lv!=null) {
            StatusMessage msg = lv.statusMessages.get(ri);
            if (msg!=null) msg.updateBody(true, true);
          }
        }
      }
      
      if (!m.networkViewOpen() && logMinutes!=0) {
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
    if (ri.s != mxUser.s_atomic.get()) return;
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
    public final String uid;
    public final Utils.LoggableRequest rq;
    public enum Status { RUNNING, RETRYING, CANCELED, DONE, ERROR }
    public Status status = Status.RUNNING;
    public final Vec<Event> events = new Vec<>();
    
    public RequestInfo(Instant start, MxServer s, Utils.LoggableRequest rq) {
      this.s = s;
      this.rq = rq;
      this.start = start;
      if (s==null) uid = null;
      else {
        MxLogin l = s.primaryLogin;
        uid = l==null? null : l.uid;
      }
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
      else if (obj instanceof Utils.RequestRes) {
        Utils.RequestRes v = (Utils.RequestRes) obj;
        obj = (v.bytes==null? "null" : v.bytes.length+"-element byte array");
      }
      this.obj = obj;
    }
    
    public static String objToString(Object obj) {
      if (obj instanceof Throwable) {
        StringWriter w = new StringWriter();
        ((Throwable) obj).printStackTrace(new PrintWriter(w));
        return w.toString();
      } else {
        return Objects.toString(obj);
      }
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
    public String calcPath() { return url; }
  }
}
