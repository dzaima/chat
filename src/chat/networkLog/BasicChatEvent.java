package chat.networkLog;

import chat.*;
import chat.ui.MsgNode;

import java.time.Instant;
import java.util.*;

public abstract class BasicChatEvent extends ChatEvent {
  public final NetworkLog l;
  public String sender;
  
  protected BasicChatEvent(String id, Instant time, String sender, NetworkLog l) {
    super(id, false, time, null);
    this.l = l;
    this.sender = sender;
  }
  
  public Chatroom room() { return l.room; }
  
  public final MsgNode.MsgType type() { return MsgNode.MsgType.MSG; }
  public String senderDisplay() { return sender; }
  public final String getSrc() { return "(log event)"; }
  public final boolean userEq(ChatEvent o) { return false; }
  public final void toTarget() { }
  public final void markRel(boolean on) { }
  public final boolean isDeleted() { return false; }
  public final HashMap<String, Integer> getReactions() { return null; }
  public final HashSet<String> getReceipts(View view) { return null; }
  public final boolean startsThread(View view) { return false; }
  public final void toThread() { }
}
