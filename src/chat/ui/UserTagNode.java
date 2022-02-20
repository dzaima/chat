package chat.ui;

import chat.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.types.*;

public class UserTagNode extends TextNode {
  private final ChatMain m;
  private final String userString;
  public boolean vis = true;
  
  public UserTagNode(ChatMain m, ChatEvent ev) {
    super(m.ctx, KS_NONE, VS_NONE);
    this.m = m;
    this.userString = ev.userString();
    add(new StringNode(ctx, ev.username));
  }
  
  public void hoverS() { if (vis) ctx.win().setCursor(Window.CursorType.HAND); }
  public void hoverE() {          ctx.win().setCursor(Window.CursorType.REGULAR); }
  
  public boolean mouseDown(int x, int y, Click c) {
    if (vis) c.notify(this, x, y);
    return true;
  }
  
  public void mouseUp(int x, int y, Click c) {
    if (gc.isClick(c)) {
      m.input.um.pushL("tag user");
      m.input.pasteText(userString+" ");
      m.input.um.pop();
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
}
