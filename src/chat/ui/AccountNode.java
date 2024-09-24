package chat.ui;

import chat.ChatUser;
import chat.mx.MxChatUser;
import chat.networkLog.NetworkLog;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.WrapNode;

public class AccountNode extends WrapNode {
  public ChatUser u;
  
  public AccountNode(Ctx ctx, Props props) {
    super(ctx, props);
  }
  
  public void mouseStart(int x, int y, Click c) {
    c.register(this, x, y);
  }
  
  public void mouseDown(int x, int y, Click c) {
    if (c.bR()) {
      PartialMenu m = new PartialMenu(gc);
      if (u instanceof MxChatUser) m.add(gc.getProp("chat.rooms.account.menu.networkLog").gr(), "networkLog", () -> {
        NetworkLog.open((MxChatUser) u);
      });
      m.open(ctx, c);
    }
  }
}
