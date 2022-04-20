package chat;

import dzaima.ui.node.Node;
import dzaima.ui.node.types.OverlapNode;
import dzaima.utils.JSON.Obj;
import dzaima.utils.Vec;

public abstract class ChatUser {
  public final ChatMain m;
  public final Node node;
  public final Node listNode; // list of rooms
  public final OverlapNode overlapNode; // node to overlap things over this user
  
  public ChatUser(ChatMain m) {
    this.m = m;
    node = m.accountNode.ctx.make(m.gc.getProp("chat.rooms.accountP").gr());
    listNode = node.ctx.id("roomlist");
    overlapNode = (OverlapNode) node.ctx.id("overlap");
  }
  
  public abstract Vec<Chatroom> rooms();
  public abstract void reorderRooms(Vec<Chatroom> rs);
  
  public abstract void tick();
  
  public abstract void close();
  
  public abstract String id();
  
  public abstract Obj data();
  
  public abstract void openLink(String url, HTMLParser.Type type, byte[] data);
  
  public int userCol(String name, boolean mine, boolean pill) {
    if (mine) return pill? m.colMyPill : m.colMyNick;
    int[] cs = pill? m.colOtherPills : m.colOtherNicks;
    return cs[(name.hashCode()&Integer.MAX_VALUE) % cs.length];
  }
}
