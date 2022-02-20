package chat.ui;

import dzaima.ui.gui.Graphics;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;

public class EmptyNode extends Node {
  private final Node n;
  
  public EmptyNode(Ctx ctx, Node n) {
    super(ctx, KS_NONE, VS_NONE);
    this.n = n;
    add(n);
  }
  
  public void drawCh(Graphics g, boolean full) { }
  
  public int minW() { return n.minW(); }
  public int maxW() { return n.maxW(); }
  public int minH(int w) { return n.minH(w); }
  public int maxH(int w) { return n.maxH(w); }
  
  protected void resized() { n.resize(w, h, 0, 0); }
}