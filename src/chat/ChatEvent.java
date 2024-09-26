package chat;

import chat.ui.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.config.GConfig;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.*;

import java.time.Instant;
import java.util.*;

public abstract class ChatEvent {
  public final String id; // whatever form of unique identifier per message there exists
  public final Instant time;
  public final boolean mine;
  public boolean edited;
  public String target; // identifier of message this is replying to
  
  protected ChatEvent(String id, boolean mine, Instant time, String target) { // set body yourself
    this.id = id;
    this.mine = mine;
    this.time = time;
    this.target = target;
  }
  
  private static final Props.Gen COL_IBEAM = Props.keys("color","ibeam");
  
  public void updBody(Node body) {
    GConfig gc = n.ctx.gc;
    Node nb = n.asContext? new STextNode(n.ctx, COL_IBEAM.values(gc.getProp("chat.search.ctx.color"), EnumProp.TRUE)) : new STextNode(n.ctx, true);
    if (target!=null) nb.add(new ReplyBtn(nb.ctx.makeHere(gc.getProp("chat.icon.replyP").gr())));
    nb.add(body);
    if (edited) nb.add(nb.ctx.make(gc.getProp("chat.msg.editedEndP").gr()));
    nb.add(MsgExtraNode.createEnd(this));
    n.ctx.id("body").replace(0, nb);
  }
  public void updateExtra() {
    if (!visible) return;
    Node b = n.ctx.id("body").ch.get(0);
    b.replace(b.ch.sz-1, MsgExtraNode.createEnd(this));
  }
  
  public abstract boolean userEq(ChatEvent o);
  
  public boolean visible; // TODO remove in favor of n!=null
  public MsgNode n;
  public MsgNode show(boolean newAtEnd, boolean asContext) {
    assert !visible : id; visible = true;
    n = MsgNode.create(this, asContext);
    updateBody(newAtEnd, false);
    return n;
  }
  public void hide() {
    assert visible; visible = false;
    n = null;
  }
  
  public void mark(int mode) { // 1-edited; 2-replying to
    n.mark(mode);
  }
  public void highlight(boolean forceScroll) {
    if (visible) {
      ScrollNode.scrollTo(n, ScrollNode.Mode.NONE, forceScroll? ScrollNode.Mode.INSTANT : ScrollNode.Mode.PARTLY_OFFSCREEN);
      n.highlight();
    }
  }
  
  public abstract Chatroom room();
  public ChatMain m() { return room().m; }
  public abstract MsgNode.MsgType type();
  public abstract String senderID();
  public abstract String senderDisplay();
  public abstract boolean isDeleted();
  
  public abstract String getSrc();
  public abstract void updateBody(boolean newAtEnd, boolean ping); // should call r.m.updMessage(this, newBodyNode, newAtEnd);
  
  public abstract void markRel(boolean on);
  public abstract void rightClick(Click c, int x, int y);
  
  public abstract HashMap<String, Integer> getReactions(); // null if none
  public abstract HashSet<String> getReceipts(View view); // null if none
  public abstract boolean startsThread(View view);
  public abstract void toTarget();
  public abstract void toThread();
  public /*open*/ void replyButtonMenu(PartialMenu pm) { }
  
  class ReplyBtn extends PadCNode {
    public ReplyBtn(Node ch) {
      super(ch.ctx, ch, 0, .05f, .1f, .1f);
    }
    public void hoverS() { ctx.vw().pushCursor(Window.CursorType.HAND); }
    public void hoverE() { ctx.vw().popCursor(); }
    
    public void mouseStart(int x, int y, Click c) { c.register(this, x, y); }
    
    public void mouseDown(int x, int y, Click c) {
      if (c.bR()) {
        PartialMenu pm = new PartialMenu(gc);
        replyButtonMenu(pm);
        pm.open(ctx, c);
      }
    }
    
    public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
    public void mouseUp(int x, int y, Click c) {
      if (visible && gc.isClick(c)) toTarget();
    }
  }
}
