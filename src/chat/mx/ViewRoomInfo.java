package chat.mx;

import chat.ChatMain;
import chat.ui.*;
import chat.utils.UnreadInfo;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;
import dzaima.utils.Vec;

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
    
    Node more = base.ctx.id("more");
    
    Vec<MxLog> logs = r.freshThreadLogs(); // checking size 0 for logs which only have read receipt info (because yeah that's a thing that happens)
    if (logs.size()>0) {
      Node threadList = m.ctx.make(m.gc.getProp("chat.roomInfo.threads").gr());
      more.add(threadList);
      for (MxLog l : logs) {
        Node n = m.ctx.make(m.gc.getProp("chat.roomInfo.threadEntry").gr());
        ((Extras.ClickableTextNode) n.ctx.id("link")).fn = () -> m.toView(l.liveView());
        Node ct = n.ctx.id("content");
        UnreadInfo u = l.unreadInfo();
        if (u.any()) {
          ct.add(RoomListNode.makeUnread(n.ctx, u));
          ct.add(new StringNode(m.ctx, " "));
        }
        ct.add(new StringNode(m.ctx, l.threadDesc(0)));
        ct.add(new InlineNode.LineEnd(base.ctx, false));
        threadList.ctx.id("more").add(n);
      }
    }
  }
  
  public static void viewRoomInfo(MxChatroom r) {
    new ViewRoomInfo(r).run();
  }
  
  public void run() {
    m.rightPanel.make(null, null).add(base);
  }
}
