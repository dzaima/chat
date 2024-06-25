package chat.ui;

import chat.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.*;
import dzaima.utils.Log;

public class UserTagNode extends TextNode {
  private final ChatMain m;
  private final String userID;
  private final boolean mine;
  private final ChatEvent ev;
  public boolean vis = true;
  
  public UserTagNode(ChatMain m, ChatEvent ev) {
    super(m.ctx, Props.none());
    this.m = m;
    this.userID = ev.senderID();
    mine = ev.mine;
    this.ev = ev;
    add(new StringNode(ctx, ev.senderDisplay()));
  }
  
  private boolean cursorPushed;
  public void hoverS() { if (vis) { ctx.vw().pushCursor(Window.CursorType.HAND); cursorPushed=true; } }
  public void hoverE() { if (cursorPushed) ctx.vw().popCursor(); }
  
  public void mouseStart(int x, int y, Click c) {
    if (vis && (c.bL() || c.bR())) c.register(this, x, y);
  }
  public void mouseDown(int x, int y, Click c) {
    if (c.bR()) {
      Chatroom r = m.room();
      if (r!=null) r.userMenu(c, x, y, userID);
    }
  }
  public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
  public void mouseUp(int x, int y, Click c) {
    if (visible) {
      LiveView lv = m.liveView();
      if (lv!=null) lv.mentionUser(userID);
    }
  }
  
  public void drawCh(Graphics g, boolean full) {
    if (vis) super.drawCh(g, full);
  }
  
  public void setVis(boolean v) {
    if (v!=vis) {
      mRedraw();
      vis = v;
    }
  }
  
  public void addInline(InlineSolver sv) {
    if (p instanceof InlineNode && sv.resize) {
      dx = dy = 0;
      w = sv.w;
    }
    int pFG = sv.tcol;
    
    sv.tcol = ev.room().user().userCol(userID, mine, false);
    
    for (Node c : ch) sv.add(c);
    sv.tcol = pFG;
  }
}
