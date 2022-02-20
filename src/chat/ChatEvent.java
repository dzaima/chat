package chat;

import chat.ui.MsgNode;
import dzaima.ui.node.types.ScrollNode;

import java.time.Instant;

public abstract class ChatEvent {
  public final String id; // whatever form of unique identifier per message there exists
  public String target; // identifier of message this is replying to
  public boolean mine;
  public String username;
  public String body;
  public Instant time;
  public boolean edited;
  
  protected ChatEvent(String id, String target) {
    this.id = id;
    this.target = target;
  }
  
  public abstract boolean userEq(ChatEvent o);
  
  public boolean visible;
  public MsgNode n;
  public MsgNode show(boolean live) {
    assert !visible; visible = true;
    n = room().m.createMessage(this);
    updateBody(live);
    return n;
  }
  public void hide() {
    assert visible; visible = false;
    n = null;
  }
  public void mark(int mode) { // 1-edited; 2-replying to
    n.mark(mode);
  }
  public void highlight(boolean forceScroll) {
    if (visible) {
      ScrollNode.scrollTo(n, forceScroll);
      n.highlight();
    }
  }
  public abstract Chatroom room();
  
  public abstract void toTarget();
  public abstract void markRel(boolean on);
  public abstract void updateBody(boolean live);
  public abstract String getSrc();
  
  public abstract MsgNode.MsgType type();
  
  public abstract String userString();
}
