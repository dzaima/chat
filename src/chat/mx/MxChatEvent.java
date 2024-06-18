package chat.mx;

import chat.*;
import chat.ui.ViewSource;
import dzaima.ui.eval.PNodeGroup;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.Prop;
import dzaima.ui.node.types.InlineNode;
import dzaima.utils.*;
import libMx.*;

import java.util.*;

public abstract class MxChatEvent extends ChatEvent {
  public final MxChatroom r;
  public final MxLog log;
  public final MxEvent e0;
  public MxEvent lastEvent;
  public String body;
  public String src;
  public String type = "?";
  public int monotonicID;
  
  public MxChatEvent(MxLog log, boolean mine, MxEvent e0, String id, String username, String target) {
    super(id, mine, e0.time, username, target);
    this.log = log;
    this.r = log.r;
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
  
  public abstract boolean important();
  
  public void markRel(boolean on) {
    // what this is replying to
    MxChatEvent r = log.get(target);
    if (r!=null && r.n!=null) r.n.setRelBg(on);
    
    // what replies to this
    Vec<String> replies = log.getReplies(id);
    if (replies!=null) for (String c : replies) {
      MxChatEvent e = log.get(c);
      if (e!=null && e.n!=null) e.n.setRelBg(on);
    }
  }
  
  public String userString() { return e0.uid; }
  
  public void rightClick(Click c, int x, int y) {
    PNodeGroup gr = n.gc.getProp("chat.mx.msgMenu.main").gr().copy(); // TODO modernize
    n.border.openMenu(true);
    Node code = null;
    if (this instanceof MxChatMessage) {
      boolean search = false;
      if (r.m.view instanceof SearchView) {
        gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.search").gr().ch);
        search = true;
      }
      
      if (!search) gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.reply").gr().ch);
      
      gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.copyLink").gr().ch);
      
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
      if (code==null) gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.text").gr().ch);
      else gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.code").gr().ch);
      
      gr.ch.add(n.gc.getProp("chat.mx.msgMenu.sep").gr());
      if (mine && !isDeleted() && !search) gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.mine").gr().ch);
    }
    gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.dev").gr().ch);
    
    Node finalCode = code;
    Popup.rightClickMenu(n.gc, n.ctx, gr, cmd -> {
      switch (cmd) { default: Log.warn("chat", "Unknown menu option "+cmd); break;
        case "(closed)":
          if (n!=null) n.border.openMenu(false);
          break;
        case "copyText":
          Node nd = r.m.getMsgBody(n);
          r.m.copyString(InlineNode.getNodeText(nd));
          break;
        case "copyCode":
          r.m.copyString(InlineNode.getNodeText(finalCode));
          break;
        case "delete":
          r.delete(this);
          break;
        case "copyLink":
          r.m.copyString(r.r.linkMsg(id));
          break;
        case "edit": {
          LiveView lv = r.currLiveView();
          if (lv != null) {
            if (lv.input.editing==null) lv.input.setEdit(this);
            lv.input.focusMe();
          }
          break;
        }
        case "replyTo": {
          LiveView lv = r.m.liveView();
          if (lv != null) {
            lv.input.markReply(this);
            lv.input.focusMe();
          }
          break;
        }
        case "goto": { // for search
          if (!(r.m.view instanceof SearchView)) break;
          View ov = ((SearchView) r.m.view).originalView;
          if (ov.contains(this)) r.m.toView(ov, this);
          else r.highlightMessage(id, null, false);
          break;
        }
        case "viewSource":
          StringBuilder b = new StringBuilder();
          if (lastEvent!=e0) {
            b.append("// initial event:\n");
            b.append(e0.o.toString(2));
            b.append("\n\n\n// latest edit:\n");
          }
          b.append(lastEvent.o.toString(2));
          
          new ViewSource(r.m, b.toString()).open();
          break;
      }
    }).takeClick(c);
  }
  
  public void delete(JSON.Obj ev) {
    Log.fine("mx", id+" has been deleted");
    type = "deleted";
    edited = false;
    target = null;
    lastEvent = new MxEvent(r.r, ev);
    updateBody(true);
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
    r.m.updateExtra(this);
  }
  
  public HashMap<String, Integer> getReactions() {
    return reactions;
  }
  
  public HashSet<String> getReceipts() {
    return r.messageReceipts.getSetForA(id);
  }
  
}
