package chat.mx;

import chat.*;
import dzaima.utils.Vec;
import libMx.MxEvent;

abstract class MxChatEvent extends ChatEvent {
  public final MxChatroom r;
  public final MxLog log;
  public final MxEvent e;
  public String src;
  public String type = "?";
  
  public MxChatEvent(MxLog log, MxEvent e, String id, String target) {
    super(id, target);
    this.log = log;
    this.r = log.r;
    this.e = e;
    time = e.time;
  }
  
  public Chatroom room() {
    return r;
  }
  
  public abstract boolean important();
  
  public void markRel(boolean on) {
    // what this is replying to
    MxChatEvent r = log.get(target);
    if (r!=null && r.n!=null) r.n.setRelBg(on);
    
    // what replies to this
    Vec<String> replies = log.getReplies(id);
    if (replies!=null) for (String c : replies) {
      MxChatEvent e = log.get(c);
      if (e!=null && e.n!=null) e.n.setRelBg(on);
    }
  }
  
  public String userString() {
    return e.uid;
  }
}
