package chat.mx;

import chat.ChatEvent;
import dzaima.utils.*;
import libMx.MxEvent;

import java.util.*;

public class MxLog {
  private static final boolean DEBUG_EVENTS = false;
  public final MxChatroom r;
  public final String threadID;
  public MxLiveView lv;
  
  public boolean globalPaging = true;
  public final Vec<MxChatEvent> list = new Vec<>();
  private final HashMap<String, MxChatEvent> msgMap = new HashMap<>(); // id → message
  
  private final HashMap<String, Vec<String>> msgReplies = new HashMap<>(); // id → ids of messages replying to it
  public static class Reaction { MxChatEvent to; String key; }
  
  public MxLog(MxChatroom r, String threadID, MxLiveView liveView) {
    this.r = r;
    this.threadID = threadID;
    lv = liveView;
  }
  
  public MxLiveView liveView() {
    if (lv == null) lv = new MxLiveView(r, this);
    return lv;
  }
  
  public MxChatEvent get(String id) {
    return msgMap.get(id);
  }
  public boolean contains(ChatEvent ev) {
    return ev instanceof MxChatEvent && msgMap.get(ev.id)==ev;
  }
  public int size() {
    return list.sz;
  }
  public Vec<String> getReplies(String id) {
    return msgReplies.get(id);
  }
  
  public void addEvents(Iterable<MxEvent> it, boolean atEnd) {
    Vec<MxChatEvent> evs = new Vec<>();
    for (MxEvent e : it) {
      MxChatEvent ev = putEvent(e, false);
      if (ev!=null) evs.add(ev);
    }
    addCompleteMessages(atEnd, evs);
  }
  
  public void addCompleteMessages(boolean atEnd, Vec<MxChatEvent> evs) {
    for (MxChatEvent e : evs) putCompleteMessage(e);
    list.insert(atEnd? list.sz : 0, evs);
    if (open) r.m.insertMessages(atEnd, evs);
  }
  
  public MxChatEvent addEventAtEnd(MxEvent e) {
    int pos = size();
    MxChatEvent cm = putEvent(e, true);
    if (cm!=null) {
      list.insert(pos, cm);
      if (open) r.m.addMessage(cm, true);
    }
    return cm;
  }
  
  private MxChatEvent putEvent(MxEvent e, boolean live) { // creates & adds message to unordered collections, but leaves ordered placements to callee
    MxChatEvent ev = processEvent(r, e, live);
    if (ev!=null) putCompleteMessage(ev);
    return ev;
  }
  
  private void putCompleteMessage(MxChatEvent ev) {
    msgMap.put(ev.id, ev);
    r.allKnownEvents.put(ev.id, ev);
    if (ev.e0.m!=null && ev.e0.m.replyId!=null) {
      msgReplies.computeIfAbsent(ev.e0.m.replyId, k->new Vec<>(2)).add(ev.id);
    }
  }
  
  // TODO move to MxChatroom?
  private static MxChatEvent processEvent(MxChatroom r, MxEvent e, boolean live) { // returns message that would be shown, or null if it's not to be displayed
    if (e.type.equals("m.reaction")) {
      JSON.Obj o = JSON.Obj.objPath(e.ct, JSON.Obj.E, "m.relates_to");
      if (live) {
        if (o.str("rel_type","").equals("m.annotation")) {
          String key = o.str("key", "");
          String r_id = o.str("event_id", "");
          MxChatEvent r_ce = r.allKnownEvents.get(r_id);
          Log.fine("mx reaction", "Reaction "+key+" added to "+r_id);
          
          if (r_ce!=null) {
            r_ce.addReaction(key, 1);
            Reaction obj = new Reaction();
            obj.to = r_ce;
            obj.key = key;
            r.reactions.put(e.id, obj);
          } else Log.fine("mx reaction", "Reaction was for unknown message");
        } else if (o.size()!=0) {
          Log.warn("mx reaction", "Unknown content[\"m.relates_to\"].rel_type value");
        }
      }
      return makeDebugNotice(r, e, live);
    } else if (e.type.equals("m.room.redaction")) {
      Reaction re = r.reactions.get(e.o.str("redacts", ""));
      if (re != null) {
        Log.fine("mx reaction", "Reaction "+re.key+" removed from "+re.to.id);
        r.reactions.remove(e.id);
        re.to.addReaction(re.key, -1);
      }
      return makeDebugNotice(r, e, live);
    }
    
    if (e.m==null) {
      return new MxChatNotice(r, e, live);
    } else {
      if (e.m.isEditEvent()) {
        MxChatEvent prev = r.allKnownEvents.get(e.m.editsId);
        if (prev instanceof MxChatMessage) {
          ((MxChatMessage) prev).edit(e, live);
          // prev.log.msgMap.put(e.id, prev);
        } // else, it's an edit of a message further back in the log
        return makeDebugNotice(r, e, live);
      } else {
        return new MxChatMessage(e.m, e, r, live);
      }
    }
  }
  
  private static MxChatNotice makeDebugNotice(MxChatroom r, MxEvent e, boolean live) {
    if (DEBUG_EVENTS) return new MxChatNotice(r, e, live);
    return null;
  }
  
  public ChatEvent prevMsg(ChatEvent msg, boolean mine) {
    int i = list.indexOf((MxChatEvent) msg);
    if (i==-1) i = list.sz;
    while (--i>=0) if ((!mine || list.get(i).mine) && !list.get(i).isDeleted()) return list.get(i);
    return msg;
  }
  
  public ChatEvent nextMsg(ChatEvent msg, boolean mine) {
    int i = list.indexOf((MxChatEvent) msg);
    if (i==-1) return null;
    while (++i< list.sz) if ((!mine || list.get(i).mine) && !list.get(i).isDeleted()) return list.get(i);
    return null;
  }
  
  public void completelyClear() {
    list.clear();
    msgMap.clear();
    msgReplies.clear();
  }
  
  
  
  public boolean open;
  public void show() {
    open = true;
    for (MxChatEvent c : list) r.m.addMessage(c, false);
  }
  public void hide() {
    open = false;
    for (MxChatEvent c : list) c.hide();
  }
}
