package chat;

import chat.ui.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.utils.JSON.Obj;
import dzaima.utils.Vec;

import java.util.function.*;

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
  public abstract void saveRooms();
  
  public abstract void tick();
  public abstract void close();
  public abstract String id();
  public abstract Obj data();
  
  public abstract void openLink(String url, Extras.LinkType type, byte[] data);
  public abstract void loadImg(String url, Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, Supplier<Boolean> stillNeeded);
  
  public int userCol(String name, boolean mine, boolean pill) {
    if (mine) return pill? m.colMyPill : m.colMyNick;
    int[] cs = pill? m.colOtherPills : m.colOtherNicks;
    return cs[(name.hashCode()&Integer.MAX_VALUE) % cs.length];
  }
  
  public void roomListChanged() {
    // TODO undo/redo setup
    roomListNode.recalculateDepths();
    saveRooms();
  }
}
