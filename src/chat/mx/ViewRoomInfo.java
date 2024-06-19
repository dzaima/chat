package chat.mx;

import chat.ChatMain;
import chat.ui.Extras;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.StringNode;

public class ViewRoomInfo {
  private final ChatMain m;
  private final Node base;
  
  ViewRoomInfo(MxChatroom r) {
    this.m = r.m;
    this.base = m.ctx.make(m.gc.getProp("chat.roomInfo.ui").gr());
    base.ctx.id("name").add(new StringNode(m.ctx, r.title()));
    base.ctx.id("server").add(new StringNode(m.ctx, r.prettyID()));
    if (r.description!=null) {
      String d = r.description;
      base.ctx.id("description").add(new StringNode(m.ctx, d.endsWith("\n")? d.substring(0, d.length()-1) : d));
    }
    
    ((Extras.ClickableTextNode) base.ctx.id("userList")).fn = r::viewUsers;
    Node count = base.ctx.id("memberCount");
    r.doubleUserList((userData, lazy) -> count.replace(0, new StringNode(base.ctx, String.valueOf(r.getJoinedMemberCount()))));
  }
  
  public static void viewRoomInfo(MxChatroom r) {
    new ViewRoomInfo(r).run();
  }
  
  public void run() {
    m.rightPanel.make(null, null).add(base);
  }
}
