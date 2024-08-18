package chat.networkLog;

import chat.*;
import chat.ui.ViewSource;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import libMx.*;

import java.time.*;
import java.util.HashMap;
import java.util.regex.*;

public class StatusMessage extends BasicChatEvent {
  private static final Pattern MATRIX = Pattern.compile("^_matrix/client/([vr]\\d)/");
  public final NetworkLog.RequestInfo ri;
  private final String body;
  
  protected StatusMessage(NetworkLog l, NetworkLog.RequestInfo ri) {
    super(String.valueOf(ri.id), ri.start, fmtTime(ri.start), l);
    this.ri = ri;
    
    String p = MxServer.redactAccessToken(ri.rq.calcURL());
    Matcher m = MATRIX.matcher(p);
    if (m.find()) p = m.group(1) + " " + p.substring(m.group().length());
    
    body = (l.m.users.sz==1 || ri.uid==null? "" : ri.uid+": ")+ri.rq.t.toString()+" "+p;
  }
  
  public static String fmtTime(Instant time) {
    LocalTime t = time.atZone(ZoneId.systemDefault()).toLocalTime();
    return t.withNano(t.getNano()/1000000*1000000).toString();
  }
  
  public void updateBody(boolean newAtEnd, boolean ping) {
    Ctx.WindowCtx ctx = l.m.ctx;
    TextNode n = new TextNode(ctx, Props.none());
    
    Props statusInfo = ctx.finishProps(ctx.gc.getProp("chat.networkLog.statuses").gr(), null);
    Props ps = ctx.finishProps(statusInfo.get(ri.status.toString().toLowerCase()).gr(), null);
    TextNode status = new TextNode(ctx, ps);
    status.add(new StringNode(ctx, ps.get("text").str()));
    n.add(status);
    
    n.add(new StringNode(ctx, " "+body));
    l.m.updMessage(this, n, ping);
  }
  
  
  public void rightClick(Click c, int x, int y) {
    PartialMenu pm = new PartialMenu(l.m.gc);
    pm.add("events", () -> l.m.toViewDirect(new EventView(l.m, ri)));
    if (ri.rq.ct!=null) pm.add("request body", () -> {
      String ct = ri.rq.ct;
      try {
        ct = JSON.parse(ct).toString(2);
      } catch (Throwable ignored) { }
      new ViewSource(l.m, ct).open();
    });
    pm.open(l.m.ctx, c);
  }
  
  private static final HashMap<String, String> uniqueStrings = new HashMap<>();
  public String senderID() {
    return uniqueStrings.computeIfAbsent(ri.rq.t.toString()+" "+(ri.uid==null? "" : ri.uid), s -> String.valueOf(uniqueStrings.size()));
  }
  
  public class EventView extends BasicNetworkView {
    public final NetworkLog.RequestInfo ri;
    public EventView(ChatMain m, NetworkLog.RequestInfo ri) { super(m); this.ri = ri; }
    
    public Chatroom room() { return l.room; }
    public void show() {
      for (NetworkLog.Event c : ri.events) addEvent(c);
      l.m.updateCurrentViewTitle();
    }
    
    private final Vec<ChatEvent> visEvents = new Vec<>();
    public void addEvent(NetworkLog.Event c) {
      l.m.addMessage(visEvents.add(new StatusEvent(l, c)), true);
    }
    
    public void hide() {
      for (ChatEvent c : visEvents) if (c.visible) c.hide();
    }
    public String title() { return "Network log request details"; }
    public boolean actionKey(Key key, KeyAction a) {
      return l.m.onCancel(key, a, () -> l.m.toRoom(l, StatusMessage.this)) || super.actionKey(key, a);
    }
  }
}
