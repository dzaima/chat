package chat;

import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Prop;
import dzaima.ui.node.types.*;
import dzaima.utils.JSON.Obj;
import dzaima.utils.Vec;

public abstract class ChatUser {
  public final ChatMain m;
  public final Node node;
  public final RoomListNode roomListNode; // list of rooms
  
  public ChatUser(ChatMain m) {
    this.m = m;
    node = m.accountNode.ctx.make(m.gc.getProp("chat.rooms.accountP").gr());
    roomListNode = (RoomListNode) node.ctx.id("roomlist");
    roomListNode.u = this;
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
  
  public static class RoomListNode extends ReorderableNode {
    public ChatUser u;
  
    public RoomListNode(Ctx ctx, String[] ks, Prop[] vs) {
      super(ctx, ks, vs);
    }
  
    public boolean shouldReorder(int idx, Node n) {
      return n instanceof Chatroom.RNode;
    }
  
    public void reorderStarted(Node n) {
      ((Chatroom.RNode) n).setBG();
    }
    
    public void reorderEnded(int oldIdx, int newIdx, Node n) {
      ((Chatroom.RNode) n).setBG();
      if (oldIdx!=newIdx) {
        Vec<Chatroom> rs = new Vec<>();
        for (Node c : ch) rs.add(((Chatroom.RNode) c).r);
        u.reorderRooms(rs);
      }
    }
  }
}
