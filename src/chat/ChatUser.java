package chat;

import dzaima.ui.node.Node;
import dzaima.ui.node.types.OverlapNode;
import dzaima.utils.*;
import dzaima.utils.JSON.Obj;

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
  
  public abstract void openLink(String url);
}
