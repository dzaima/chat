package chat.networkLog;

import chat.ui.ViewSource;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.Log;

import java.io.*;
import java.util.Objects;

public class StatusEvent extends BasicChatEvent {
  private static final int MAX_PREVIEW = 100;
  public final NetworkLog.Event ev;
  public final Object obj;
  
  public StatusEvent(NetworkLog l, NetworkLog.Event ev) {
    super(String.valueOf(ev.id), ev.when, "", l);
    this.ev = ev;
    this.obj = ev.obj;
    
    username = StatusMessage.fmtTime(time);
  }
  
  public void updateBody(boolean live) {
    String msg = ev.type;
    try {
      if (obj!=null) {
        String s = obj instanceof NetworkLog.CompactJSON? ((NetworkLog.CompactJSON) obj).str : Objects.toString(obj).replaceAll("\n", "");
        msg+= ": "+(s.length()> MAX_PREVIEW? s.substring(0, MAX_PREVIEW)+"…" : s);
      }
    } catch (Throwable ignored) { msg+= "; error while formatting"; }
    l.m.updMessage(this, new StringNode(l.m.ctx, msg), live);
  }
  
  public String userString() { return ev.type; }
  
  public void rightClick(Click c, int x, int y) {
    PartialMenu pm = new PartialMenu(l.m.gc);
    if (obj!=null) pm.add("View full", () -> {
      try {
        String s;
        if (obj instanceof Throwable) {
          StringWriter w = new StringWriter();
          ((Throwable) obj).printStackTrace(new PrintWriter(w));
          s = w.toString();
        } else {
          s = Objects.toString(obj);
        }
        new ViewSource(l.m, s).open();
      } catch (Throwable t) {
        Log.stacktrace("network-log", t);
      }
    });
    pm.open(l.m.ctx, c);
  }
  
}
