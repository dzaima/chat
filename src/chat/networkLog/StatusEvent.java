package chat.networkLog;

import chat.ui.ViewSource;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.*;

import java.io.*;
import java.util.Objects;
import java.util.function.Supplier;

public class StatusEvent extends BasicChatEvent {
  private static final int MAX_PREVIEW = 100;
  public final NetworkLog.Event ev;
  public final Object obj;
  
  public StatusEvent(NetworkLog l, NetworkLog.Event ev) {
    super(String.valueOf(ev.id), ev.when, StatusMessage.fmtTime(ev.when), l);
    this.ev = ev;
    this.obj = ev.obj;
  }
  
  public void updateBody(boolean newAtEnd, boolean ping) {
    String msg = ev.type;
    try {
      if (obj!=null) {
        String s = obj instanceof NetworkLog.CompactJSON? ((NetworkLog.CompactJSON) obj).str : Objects.toString(obj).replaceAll("\n", "");
        msg+= ": "+(s.length()> MAX_PREVIEW? s.substring(0, MAX_PREVIEW)+"…" : s);
      }
    } catch (Throwable ignored) { msg+= "; error while formatting"; }
    l.m.updMessage(this, new StringNode(l.m.ctx, msg), ping);
  }
  
  public String senderID() { return ev.type; }
  
  public void rightClick(Click c, int x, int y) {
    PartialMenu pm = new PartialMenu(l.m.gc);
    if (obj!=null) {
      pm.add("View full", () -> {
        try {
          new ViewSource(l.m, NetworkLog.Event.objToString(obj)).open();
        } catch (Throwable t) {
          Log.stacktrace("network-log", t);
        }
      });
      pm.add("Save to file", () -> m().saveFile(null, null, null, p -> {
        if (p!=null) Tools.writeFile(p, NetworkLog.Event.objToString(obj));
      }));
    }
    pm.open(l.m.ctx, c);
  }
  
}
