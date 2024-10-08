package chat.mx;

import chat.*;
import chat.ui.ViewSource;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.Prop;
import dzaima.ui.node.types.InlineNode;
import dzaima.utils.*;
import libMx.MxEvent;

import java.util.*;

public abstract class MxChatEvent extends ChatEvent {
  public final MxChatroom r;
  public final MxEvent e0;
  public MxEvent lastEvent;
  public String body;
  public String src;
  public String type = "?";
  public int monotonicID;
  public boolean hasThread;
  
  public MxChatEvent(MxChatroom r, boolean mine, MxEvent e0, String id, String target) {
    super(id, mine, e0.time, target);
    assert !r.allKnownEvents.containsKey(id);
    this.r = r;
    this.e0 = e0;
    this.lastEvent = e0;
  }
  
  protected void loadReactions() {
    for (JSON.Obj c : JSON.Obj.arrPath(e0.o, JSON.Arr.E, "unsigned", "m.relations", "m.annotation", "chunk").objs()) {
      if ("m.reaction".equals(c.str("type",""))) {
        int count = c.getInt("count", 0);
        String key = c.str("key", "");
        Log.fine("mx reaction", "Loading initial reaction "+key+"×"+count+" for "+id);
        addReaction(key, count);
      }
    }
  }
  
  public Chatroom room() { return r; }
  
  public boolean isDeleted() {
    return type.equals("deleted");
  }
  
  public abstract boolean increasesUnread();
  
  public void markRel(boolean on) {
    MxLog log = r.visibleLog();
    if (log==null) return;
    
    // what this is replying to
    MxChatEvent re = r.editRootEvent(target);
    if (re!=null && re.n!=null) re.n.setRelBg(on);
    
    // what replies to this
    Vec<String> replies = log.getReplies(id);
    if (replies!=null) for (String c : replies) {
      MxChatEvent e = log.get(c);
      if (e!=null && e.n!=null) e.n.setRelBg(on);
    }
  }
  
  public String senderID() { return e0.uid; }
  
  public void rightClick(Click c, int x, int y) {
    n.border.openMenu(true);
    PartialMenu pm = new PartialMenu(r.m.gc);
    
    Node code = null;
    if (this instanceof MxChatMessage) {
      boolean search = r.m.view instanceof SearchView;
      if (search) {
        pm.add(n.gc.getProp("chat.mx.msgMenu.search").gr(), "goto", () -> {
          if (!(r.m.view instanceof SearchView)) return;
          View ov = ((SearchView) r.m.view).originalView;
          if (ov.contains(this)) r.m.toView(ov, this);
          else r.highlightMessage(id, null, false);
        });
      } else {
        pm.add(n.gc.getProp("chat.mx.msgMenu.reply").gr(), "replyTo", () -> {
          LiveView lv = r.m.liveView();
          if (lv != null) {
            lv.input.markReply(this);
            lv.input.focusMe();
          }
        });
      }
      
      pm.add(n.gc.getProp("chat.mx.msgMenu.copyLink").gr(), "copyLink", () -> r.m.copyString(r.r.linkMsg(id)));
      
      Node cn = n.border;
      while (true) {
        Node nn = cn.findCh(x, y);
        if (nn==null) break;
        Prop cl = nn.getPropN("class");
        if (cl!=null) {
          String v = cl.val();
          if (v.equals("inlineCode") || v.equals("blockCode")) code = nn;
        }
        x-= nn.dx;
        y-= nn.dy;
        cn = nn;
      }
      if (code==null) {
        pm.add(n.gc.getProp("chat.mx.msgMenu.text").gr(), "copyText", () -> {
          Node b = n.ctx.id("body");
          r.m.copyString(InlineNode.getNodeText(b.ch.get(0)));
        });
      } else {
        Node finalCode = code;
        pm.add(n.gc.getProp("chat.mx.msgMenu.code").gr(), "copyCode", () -> r.m.copyString(InlineNode.getNodeText(finalCode)));
      }
      
      MxLiveView lv = r.currLiveView();
      if (lv!=null && lv.log.isMain()) pm.add(n.gc.getProp("chat.mx.msgMenu.openThread").gr(), "openThread", this::toThread);
      
      pm.addSep();
      
      if (mine && !isDeleted() && !search) pm.add(n.gc.getProp("chat.mx.msgMenu.mine").gr(), (s) -> {
        if (s.equals("delete")) {
          r.delete(this);
          return true;
        }
        if (s.equals("edit")) {
          if (lv!=null) {
            if (lv.input.editing==null) lv.input.setEdit(this);
            lv.input.focusMe();
          }
          return true;
        }
        return false;
      });
    }
    
    pm.add(n.gc.getProp("chat.mx.msgMenu.dev").gr(), "viewSource", () -> {
      StringBuilder b = new StringBuilder();
      if (lastEvent!=e0) {
        b.append("// initial event:\n");
        b.append(e0.o.toString(2));
        b.append("\n\n\n// latest edit:\n");
      }
      b.append(lastEvent.o.toString(2));
      
      new ViewSource(r.m, b.toString()).open();
    });
    
    pm.open(r.m.ctx, c, () -> {
      if (n!=null) n.border.openMenu(false);
    });
  }
  
  public void delete(JSON.Obj ev) {
    Log.fine("mx", id+" has been deleted");
    type = "deleted";
    edited = false;
    target = null;
    lastEvent = new MxEvent(r.r, ev);
    updateBody(true, false);
  }
  
  HashMap<String, Integer> reactions;
  public void addReaction(String key, int d) {
    if (d==0) return;
    if (reactions==null) reactions = new HashMap<>();
    int nv = reactions.getOrDefault(key, 0) + d;
    if (nv!=0) reactions.put(key, nv);
    else {
      reactions.remove(key);
      if (reactions.isEmpty()) reactions = null;
    }
    updateExtra();
  }
  
  public HashMap<String, Integer> getReactions() {
    return reactions;
  }
  
  public HashSet<String> getReceipts(View view) {
    MxLog log = r.logOfView(view);
    return log==null? null : log.messageReceipts.getSetForA(id);
  }
  
  public boolean startsThread(View view) {
    MxLog log = r.logOfView(view);
    return hasThread && log!=null && log.isMain();
  }
  
  public void markHasThread() {
    if (!hasThread) {
      hasThread = true;
      updateExtra();
    }
  }
  
  public void toThread() {
    r.m.toView(r.getThreadLog(id).liveView());
  }
  
  public void replyButtonMenu(PartialMenu pm) {
    if (target!=null) pm.add(r.m.gc.getProp("chat.mx.msgMenu.onReply").gr(), "copyLink", () -> r.m.copyString(r.r.linkMsg(target)));
  }
}
