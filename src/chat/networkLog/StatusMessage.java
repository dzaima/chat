package chat.networkLog;

import chat.*;
import chat.ui.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.*;
import dzaima.utils.JSON;
import libMx.*;

import java.time.*;
import java.util.*;
import java.util.regex.*;

public class StatusMessage extends ChatEvent {
  private static final Pattern MATRIX = Pattern.compile("^_matrix/client/([vr]\\d)/");
  public final NetworkLog l;
  public final NetworkLog.RequestInfo ri;
  private final String body;
  
  protected StatusMessage(NetworkLog l, NetworkLog.RequestInfo ri) {
    super(String.valueOf(ri.id), false, ri.start, "?", null);
    this.l = l;
    this.ri = ri;
    
    String p = MxServer.redactAccessToken(ri.rq.calcURL());
    Matcher m = MATRIX.matcher(p);
    if (m.find()) p = m.group(1) + " " + p.substring(m.group().length());
    
    body = ri.rq.t.toString()+" "+p;
    
    username = fmtTime(time);
  }
  
  public static String fmtTime(Instant time) {
    LocalTime t = time.atZone(ZoneId.systemDefault()).toLocalTime();
    return t.withNano(t.getNano()/1000000*1000000).toString();
  }
  
  public Chatroom room() {
    return l.room;
  }
  
  public void updateBody(boolean live) {
    Ctx.WindowCtx ctx = l.m.ctx;
    TextNode n = new TextNode(ctx, Props.none());
    
    Props statusInfo = ctx.finishProps(ctx.gc.getProp("chat.networkLog.statuses").gr(), null);
    Props ps = ctx.finishProps(statusInfo.get(ri.status.toString().toLowerCase()).gr(), null);
    TextNode status = new TextNode(ctx, ps);
    status.add(new StringNode(ctx, ps.get("text").str()));
    n.add(status);
    
    n.add(new StringNode(ctx, " "+body));
    l.m.updMessage(this, n, live);
  }
  
  
  public void rightClick(Click c, int x, int y) {
    PartialMenu pm = new PartialMenu(l.m.gc);
    pm.add("events", () -> {
      l.m.toViewDirect(new View() {
        public Chatroom room() { return l.room; }
        public void openViewTick() { }
        public void show() {
          for (NetworkLog.Event c : ri.events) {
            l.m.addMessage(new StatusEvent(l, c), true);
          }
          l.m.updateCurrentViewTitle();
        }
        public void hide() { }
        public String title() { return "Network log request details"; }
        public boolean key(Key key, int scancode, KeyAction a) {
          if (l.m.gc.keymap(key, a, "chat").equals("cancel")) {
            l.m.toViewDirect(l);
            highlight(true);
            return true;
          }
          return false;
        }
        public boolean typed(int codepoint) { return false; }
        public String asCodeblock(String s) { return s; }
        public LiveView baseLiveView() { return l.baseLiveView(); }
        public boolean contains(ChatEvent ev) { return false; } // TODO?
      });
    });
    if (ri.rq.ct!=null) pm.add("request body", () -> {
      String ct = ri.rq.ct;
      try {
        ct = JSON.parse(ct).toString(2);
      } catch (Throwable ignored) { }
      new ViewSource(l.m, ct).open();
    });
    pm.open(l.m.ctx, c);
  }
  
  public String userString() {
    MxLogin l = ri.s.primaryLogin;
    return ri.rq.t.toString()+" "+(l==null? "" : l.uid);
  }
  
  public String getSrc() { return "(log event)"; }
  public MsgNode.MsgType type() { return MsgNode.MsgType.MSG; }
  public boolean userEq(ChatEvent o) { return false; }
  public void toTarget() { }
  public void markRel(boolean on) { }
  public boolean isDeleted() { return false; }
  public HashMap<String, Integer> getReactions() { return null; }
  public HashSet<String> getReceipts() { return null; }
}
