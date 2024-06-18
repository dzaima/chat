package chat.networkLog;

import chat.*;
import chat.ui.MsgNode;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.*;
import libMx.MxServer;

import java.time.*;
import java.util.*;
import java.util.regex.*;

public class StatusMessage extends ChatEvent {
  private static final Pattern MATRIX = Pattern.compile("^_matrix/client/([vr]\\d)/");
  public final NetworkLog l;
  public final NetworkLog.RequestStatus st;
  private final String body;
  
  protected StatusMessage(NetworkLog l, NetworkLog.RequestStatus st) {
    super(String.valueOf(st.id), false, st.start, "?", null);
    this.l = l;
    this.st = st;
    
    String p = MxServer.redactAccessToken(st.rq.calcURL());
    Matcher m = MATRIX.matcher(p);
    if (m.find()) p = m.group(1) + " " + p.substring(m.group().length());
    
    body = st.rq.t.toString()+" "+p;
    
    LocalTime t = time.atZone(ZoneId.systemDefault()).toLocalTime();
    username = t.withNano(t.getNano()/1000000*1000000).toString();
  }
  
  public boolean userEq(ChatEvent o) {
    return false;
  }
  
  public Chatroom room() {
    return l.room;
  }
  
  public void updateBody(boolean live) {
    Ctx.WindowCtx ctx = l.m.ctx;
    TextNode n = new TextNode(ctx, Props.none());
    
    Props statusInfo = ctx.finishProps(ctx.gc.getProp("chat.networkLog.statuses").gr(), null);
    Props ps = ctx.finishProps(statusInfo.get(st.status.toString().toLowerCase()).gr(), null);
    TextNode status = new TextNode(ctx, ps);
    status.add(new StringNode(ctx, ps.get("text").str()));
    n.add(status);
    
    n.add(new StringNode(ctx, " "+body));
    l.m.updMessage(this, n, live);
  }
  
  
  public void rightClick(Click c, int x, int y) {
    
  }
  
  public void toTarget() { }
  public void markRel(boolean on) { }
  public String getSrc() { return "(log event)"; }
  public MsgNode.MsgType type() { return MsgNode.MsgType.MSG; }
  public boolean isDeleted() { return false; }
  public String userString() { return st.rq.t.toString()+" "+st.s.primaryLogin.uid; }
  
  public HashMap<String, Integer> getReactions() { return null; }
  public HashSet<String> getReceipts() { return null; }
}
