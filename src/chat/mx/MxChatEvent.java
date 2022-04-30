package chat.mx;

import chat.*;
import dzaima.ui.eval.PNodeGroup;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.StringNode;
import dzaima.ui.node.types.editable.code.CodeAreaNode;
import dzaima.utils.*;
import libMx.MxEvent;

abstract class MxChatEvent extends ChatEvent {
  public final MxChatroom r;
  public final MxLog log;
  public final MxEvent e0;
  public MxEvent lastEvent;
  public String src;
  public String type = "?";
  
  public MxChatEvent(MxLog log, MxEvent e0, String id, String target) {
    super(id, target);
    this.log = log;
    this.r = log.r;
    this.e0 = e0;
    this.time = e0.time;
    this.lastEvent = e0;
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
  
  public String userString() {
    return e0.uid;
  }
  
  public void rightClick(Click c, int x, int y) {
    PNodeGroup gr = n.gc.getProp("chat.mx.msgMenu.main").gr().copy();
    n.border.openMenu(true);
    Node code = null;
    if (this instanceof MxChatMessage) {
      gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.message").gr().ch);
      
      Node cn = n.border;
      while (true) {
        Node nn = cn.findCh(x, y);
        if (nn==null) break;
        int cid = nn.id("class");
        if (cid!=-1) {
          String v = nn.vs[cid].val();
          if (v.equals("inlineCode") || v.equals("blockCode")) code = nn;
        }
        x-= nn.dx;
        y-= nn.dy;
        cn = nn;
      }
      if (code==null) gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.text").gr().ch);
      else gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.code").gr().ch);
      
      gr.ch.add(n.gc.getProp("chat.mx.msgMenu.sep").gr());
      if (mine && !isDeleted()) gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.mine").gr().ch);
    }
    gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.dev").gr().ch);
    
    Node finalCode = code;
    Popup.rightClickMenu(n.gc, n.ctx, gr, cmd -> {
      switch (cmd) { default: ChatMain.warn("Unknown menu option "+cmd); break;
        case "(closed)":
          if (n!=null) n.border.openMenu(false);
          break;
        case "copyText":
          Node nd = n.ctx.id("body").ch.get(1); // "1" also being a constant in ChatMain.updMessage replace call
          r.m.copyString(StringNode.getNodeText(nd));
          break;
        case "copyCode":
          r.m.copyString(StringNode.getNodeText(finalCode));
          break;
        case "delete":
          r.delete(this);
          break;
        case "copyLink":
          r.m.copyString(r.r.linkMsg(id));
          break;
        case "edit":
          if (r.m.editing==null) r.m.setEdit(this);
          r.m.input.focusMe();
          break;
        case "replyTo":
          r.m.markReply(this);
          r.m.input.focusMe();
          break;
        case "viewSource":
          new Popup(n.ctx.win()) {
            protected void unfocused() { if (isVW) close(); }
            protected Rect fullRect() { return centered(n.ctx.vw(), 0.8, 0.8); }
            
            protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a); }
            
            protected void setup() {
              CodeAreaNode e = (CodeAreaNode) node.ctx.id("src");
              e.append(lastEvent.o.toString(2));
              e.setLang(n.gc.langs().fromName("java"));
              e.um.clear();
            }
          }.openWindow(n.gc, n.ctx, n.gc.getProp("chat.sourceUI").gr(), "Message source");
          break;
      }
    }).takeClick(c);
  }
  
  public void delete(JSON.Obj ev) {
    Log.fine("mx", id+" has been deleted");
    type = "deleted";
    target = null;
    lastEvent = new MxEvent(r.r, ev);
    updateBody(true);
  }
}
