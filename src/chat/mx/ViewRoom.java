package chat.mx;

import chat.ChatMain;
import chat.ui.Extras;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;

public class ViewRoom {
  final MxChatroom r;
  final ChatMain m;
  final Node base;
  
  ViewRoom(MxChatroom r) {
    this.r = r;
    this.m = r.m;
    this.base = m.ctx.make(m.gc.getProp("chat.roomList.ui").gr());
    base.ctx.id("name").add(new StringNode(m.ctx, r.title()));
    base.ctx.id("server").add(new StringNode(m.ctx, r.prettyID()));
    if (r.description!=null) {
      String d = r.description;
      base.ctx.id("description").add(new StringNode(m.ctx, d.endsWith("\n")? d.substring(0, d.length()-1) : d));
    }
    
    ((Extras.ClickableTextNode) base.ctx.id("userList")).fn = r::viewUsers;
    base.ctx.id("memberCount").add(new StringNode(base.ctx, String.valueOf(r.memberCount())));
  }
  
  public static void viewRooms(MxChatroom r) {
    new ViewRoom(r).run();
  }
  
  public void run() {
    m.rightPanel.make(null, null).add(base);
  }
}
