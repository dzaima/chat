package chat.ui;

import dzaima.ui.eval.*;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.*;

public class CopyMenuNode extends WrapNode {
  public CopyMenuNode(Ctx ctx, Props props) {
    super(ctx, props);
  }
  
  
  public void mouseStart(int x, int y, Click c) {
    super.mouseStart(x, y, c);
    if (c.bR()) c.register(this, x, y);
  }
  
  public void mouseDown(int x, int y, Click c) {
    if (c.bR()) {
      PartialMenu pm = new PartialMenu(gc);
      
      PNodeGroup p = gc.getProp("chat.copyMI").gr().copy();
      p.ch.add(new PNode.PNodeStr(getProp("text").str()));
      
      pm.add(p, "copy", () -> {
        ctx.win().copyString(InlineNode.getNodeText(ch.get(0)));
      });
      pm.open(ctx, c);
    }
  }
}
