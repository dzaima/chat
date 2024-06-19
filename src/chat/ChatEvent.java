package chat;

import chat.ui.MsgNode;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.ScrollNode;

import java.time.Instant;
import java.util.*;

public abstract class ChatEvent {
  public final String id; // whatever form of unique identifier per message there exists
  public final Instant time;
  public final boolean mine;
  public boolean edited;
  public String target; // identifier of message this is replying to
  public String username;
  
  protected ChatEvent(String id, boolean mine, Instant time, String username, String target) { // set body yourself
    this.id = id;
    this.mine = mine;
    this.time = time;
    this.username = username;
    this.target = target;
  }
  
  public abstract boolean userEq(ChatEvent o);
  
  public boolean visible;
  public MsgNode n;
  public MsgNode show(boolean live, boolean asContext) {
    assert !visible; visible = true;
    n = MsgNode.create(this, asContext);
    updateBody(live);
    return n;
  }
  public void hide() {
    assert visible; visible = false;
    n = null;
  }
  public Node getMsgBody() {
    Node b = n.ctx.id("body");
    return b.ch.get(0);
  }
  public void setMsgBody(Node ct) {
    Node b = n.ctx.id("body");
    b.replace(0, ct);
  }
  public void mark(int mode) { // 1-edited; 2-replying to
    n.mark(mode);
  }
  public void highlight(boolean forceScroll) {
    if (visible) {
      ScrollNode.scrollTo(n, ScrollNode.Mode.NONE, forceScroll? ScrollNode.Mode.INSTANT : ScrollNode.Mode.PARTLY_OFFSCREEN);
      n.highlight();
    }
  }
  
  public abstract Chatroom room();
  public abstract MsgNode.MsgType type();
  public abstract String userString();
  public abstract boolean isDeleted();
  
  public abstract String getSrc();
  public abstract void updateBody(boolean live); // should call m.updMessage(this, bodyNode, live) (and potentially later again with live=false)
  
  public abstract void markRel(boolean on);
  public abstract void rightClick(Click c, int x, int y);
  
  public abstract HashMap<String, Integer> getReactions(); // null if none
  public abstract HashSet<String> getReceipts(View view); // null if none
  public abstract boolean startsThread(View view);
  public abstract void toTarget();
  public abstract void toThread();
}
