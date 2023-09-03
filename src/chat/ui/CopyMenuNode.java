package chat.ui;

import chat.ChatMain;
import dzaima.ui.eval.*;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Prop;
import dzaima.ui.node.types.*;

public class CopyMenuNode extends WrapNode {
  public CopyMenuNode(Ctx ctx, String[] ks, Prop[] vs) {
    super(ctx, ks, vs);
  }
  
  
  public void mouseStart(int x, int y, Click c) {
    super.mouseStart(x, y, c);
    if (c.bR()) c.register(this, x, y);
  }
  
  public void mouseDown(int x, int y, Click c) {
    if (c.bR()) {
      PartialMenu pm = new PartialMenu(gc);
      
      PNodeGroup p = gc.getProp("chat.copyMI").gr().copy();
      p.ch.add(new PNode.PNodeStr(vs[id("text")].str()));
      
      pm.add(p, "copy", () -> {
        ctx.win().copyString(InlineNode.getNodeText(ch.get(0)));
      });
      pm.open(ctx, c);
    }
  }
}
