package chat.mx;

import chat.*;
import dzaima.ui.eval.PNodeGroup;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.types.editable.EditNode;
import dzaima.ui.node.types.editable.code.CodeAreaNode;
import dzaima.utils.*;
import libMx.MxEvent;

abstract class MxChatEvent extends ChatEvent {
  public final MxChatroom r;
  public final MxLog log;
  public final MxEvent e;
  public String src;
  public String type = "?";
  
  public MxChatEvent(MxLog log, MxEvent e, String id, String target) {
    super(id, target);
    this.log = log;
    this.r = log.r;
    this.e = e;
    time = e.time;
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
    return e.uid;
  }
  
  public void rightClick(Click c) {
    PNodeGroup gr = n.gc.getProp("chat.mx.msgMenu.main").gr().copy();
    if (this instanceof MxChatMessage) {
      gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.message").gr().ch);
      if (mine && !isDeleted()) gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.mine").gr().ch);
    }
    gr.ch.addAll(n.gc.getProp("chat.mx.msgMenu.dev").gr().ch);
    
    Popup.rightClickMenu(n.gc, n.ctx, gr, cmd -> {
      switch (cmd) { default: ChatMain.warn("Unknown menu option "+cmd); break;
        case "delete":
          r.delete(this);
          break;
        case "copyLink":
          r.m.copyString(r.r.linkMsg(id));
          break;
        case "edit":
          if (r.m.editing==null) r.m.setEdit(this);
          break;
        case "replyTo":
          r.m.markReply(this);
          break;
        case "viewSource":
          new Popup(n.ctx.win()) {
            protected void unfocused() { if (isVW) close(); }
            protected Rect fullRect() { return centered(n.ctx.vw(), 0.8, 0.8); }
            
            protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a); }
            
            protected void setup() {
              CodeAreaNode e = (CodeAreaNode) node.ctx.id("src");
              e.append(MxChatEvent.this.e.o.toString(2));
              e.setLang(n.gc.langs().fromName("java"));
              e.um.clear();
            }
          }.openWindow(n.gc, n.ctx, n.gc.getProp("chat.sourceUI").gr(), "Message source");
          break;
      }
    }).takeClick(c);
  }
}
