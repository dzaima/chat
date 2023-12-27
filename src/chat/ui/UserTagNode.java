package chat.ui;

import chat.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.*;

public class UserTagNode extends TextNode {
  private final Chatroom r;
  private final String userString;
  private final boolean mine;
  private final ChatEvent ev;
  public boolean vis = true;
  
  public UserTagNode(ChatMain m, ChatEvent ev) {
    super(m.ctx, Props.none());
    this.r = ev.room();
    this.userString = ev.userString();
    mine = ev.mine;
    this.ev = ev;
    add(new StringNode(ctx, ev.username));
  }
  
  private boolean cursorPushed;
  public void hoverS() { if (vis) { ctx.vw().pushCursor(Window.CursorType.HAND); cursorPushed=true; } }
  public void hoverE() { if (cursorPushed) ctx.vw().popCursor(); }
  
  public void mouseStart(int x, int y, Click c) {
    if (vis && (c.bL() || c.bR())) c.register(this, x, y);
  }
  public void mouseDown(int x, int y, Click c) {
    if (c.bR()) r.userMenu(c, x, y, userString);
  }
  public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
  public void mouseUp(int x, int y, Click c) {
    if (visible) r.mentionUser(userString);
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
    
    sv.tcol = r.user().userCol(userString, mine, false);
    
    for (Node c : ch) sv.add(c);
    sv.tcol = pFG;
  }
}
