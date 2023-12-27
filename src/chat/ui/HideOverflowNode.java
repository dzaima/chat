package chat.ui;

import dzaima.ui.gui.Graphics;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.FrameNode;
import dzaima.utils.Tools;

public class HideOverflowNode extends FrameNode {
  public HideOverflowNode(Ctx ctx, Props props) {
    super(ctx, props);
  }
  
  public int fillW() { Prop w = getPropN("w"); return w==null? 0 : w.len(); }
  public int fillH(int w) { return ch.get(0).minH(Tools.BIG); }
  
  
  public void drawCh(Graphics g, boolean full) {
    assert ch.size()==1;
    g.push();
    g.clip(0, 0, w, h);
    super.drawCh(g, full);
    g.pop();
  }
  
  protected void resized() {
    assert ch.size()==1;
    Node c = ch.get(0);
    int mw = c.maxW();
    if (xalign()==1) {
      if (mw > w) c.resize(mw*2, h, 0, 0); // TODO remove *2 after hProps is properly invoked where needed
      else c.resize(mw*2, h, w-mw, 0);
    } else {
      c.resize(mw*2, h, 0, 0);
    }
    mRedraw();
  }
}
