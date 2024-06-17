package chat.mx;

import chat.ChatEvent;
import dzaima.utils.*;
import libMx.MxEvent;

import java.util.*;

public class MxLog {
  public final MxChatroom r;
  public final String threadID;
  public boolean globalPaging = true;
  public final Vec<MxChatEvent> list = new Vec<>();
  public final HashSet<MxChatEvent> set = new HashSet<>();
  public final HashMap<String, MxChatEvent> msgMap = new HashMap<>(); // id → message
  public final HashMap<String, Vec<String>> msgReplies = new HashMap<>(); // id → ids of messages replying to it
  public MxLiveView lv;
  
  public static class Reaction { MxChatEvent to; String key; }
  
  public HashMap<String, Reaction> reactions = new HashMap<>();
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
    return ev instanceof MxChatEvent && set.contains(ev);
  }
  public Vec<String> getReplies(String id) {
    return msgReplies.get(id);
  }
  public void addEvents(Iterable<MxEvent> it, boolean atEnd) {
    Vec<MxChatEvent> evs = new Vec<>();
    for (MxEvent e : it) {
      MxChatEvent ev = processMessage(e, -1, false);
      if (ev!=null) {
        r.allKnownEvents.put(ev.id, ev);
        evs.add(ev);
      }
    }
    insertLog(atEnd? list.sz : 0, evs);
    if (open) r.m.insertMessages(atEnd, evs);
  }
  public int size() {
    return list.sz;
  }
  public void insertLog(int i, Vec<MxChatEvent> msgs) {
    list.insert(i, msgs);
  }
  public MxChatEvent processMessage(MxEvent e, int pos, boolean live) { // returns message that would be shown, or null if it's an edit
    if ("m.reaction".equals(e.type) && live) {
      JSON.Obj o = JSON.Obj.objPath(e.ct, JSON.Obj.E, "m.relates_to");
      if (o.str("rel_type","").equals("m.annotation")) {
        String key = o.str("key", "");
        String r_id = o.str("event_id", "");
        MxChatEvent r_ce = get(r_id);
        Log.fine("mx reaction", "Reaction "+key+" added to "+r_id);
        
        if (r_ce!=null) {
          r_ce.addReaction(key, 1);
          Reaction obj = new Reaction();
          obj.to = r_ce;
          obj.key = key;
          reactions.put(e.id, obj);
        } else Log.fine("mx reaction", "Reaction was for unknown message");
      } else if (o.size()!=0) {
        Log.info("mx reaction", "Bad content[\"m.relates_to\"].rel_type value");
      }
      return null;
    } else if ("m.room.redaction".equals(e.type)) {
      Reaction r = reactions.get(e.o.str("redacts", ""));
      if (r!=null) {
        Log.fine("mx reaction", "Reaction "+r.key+" removed from "+r.to.id);
        reactions.remove(e.id);
        r.to.addReaction(r.key, -1);
        return null;
      }
    }
    if (e.m==null) {
      MxChatNotice cm = new MxChatNotice(this, e, live);
      if (cm.ignore()) return null;
      putEvent(cm.id, cm);
      if (pos>=0) list.insert(pos, cm);
      return cm;
    } else {
      if (e.m.edit==1) {
        MxChatEvent prev = msgMap.get(e.m.editsId);
        if (prev instanceof MxChatMessage) {
          ((MxChatMessage) prev).edit(e, live);
          putEvent(e.id, prev);
        }
        // else, it's an edit of a message further back in the log
        return null;
      } else {
        MxChatEvent cm = new MxChatMessage(e.m, e, this, live);
        putEvent(cm.id, cm);
        if (pos>=0) list.insert(pos, cm);
        return cm;
      }
    }
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
  
  private void putEvent(String id, MxChatEvent m) {
    msgMap.put(id, m);
    set.add(m);
    r.allKnownEvents.put(m.id, m);
    if (m.e0.m!=null && m.e0.m.replyId!=null) {
      msgReplies.computeIfAbsent(m.e0.m.replyId, k->new Vec<>(2)).add(id);
    }
  }
  
  public void completelyClear() {
    list.clear();
    msgMap.clear();
    msgReplies.clear();
    reactions.clear();
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
