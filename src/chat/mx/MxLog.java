package chat.mx;

import chat.ChatEvent;
import dzaima.utils.*;
import libMx.MxEvent;

import java.util.HashMap;

public class MxLog {
  public final MxChatroom r;
  public final String threadID;
  public MxLiveView lv;
  
  public boolean globalPaging = true;
  public MxEvent lastEvent;
  public final Vec<MxChatEvent> list = new Vec<>();
  private final HashMap<String, MxChatEvent> msgMap = new HashMap<>(); // message id → message
  
  public final HashMap<String, String> latestReceipts = new HashMap<>(); // user ID → event ID of their receipt
  public final PairHashSetA<String, String> messageReceipts = new PairHashSetA<>(); // event ID → set of users
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
    return ev instanceof MxChatEvent && get(ev.id)==ev;
  }
  public int size() {
    return list.sz;
  }
  Pair<Integer, Boolean> unreadInfo() {
    return new Pair<>(r.unreads.getForA(this).size(), !r.pings.getForA(this).isEmpty());
  }
  public Vec<String> getReplies(String id) {
    return msgReplies.get(id);
  }
  
  public Vec<MxChatEvent> addEvents(Iterable<MxEvent> it, boolean atEnd) {
    Vec<MxChatEvent> evs = new Vec<>();
    for (MxEvent e : it) {
      if (atEnd) lastEvent = e;
      MxChatEvent ev = r.processEvent(e, false);
      if (ev==null) continue;
      evs.add(ev);
    }
    addCompleteMessages(atEnd, evs);
    return evs;
  }
  
  public void addCompleteMessages(boolean atEnd, Vec<MxChatEvent> evs) {
    list.insert(atEnd? list.sz : 0, evs);
    for (MxChatEvent e : evs) putCompleteMessage(e);
    if (open) r.m.insertMessages(atEnd, evs);
  }
  
  public MxChatEvent addEventAtEnd(MxEvent e) {
    lastEvent = e;
    int pos = size();
    MxChatEvent ev = r.processEvent(e, true);
    if (ev!=null) {
      list.insert(pos, ev);
      putCompleteMessage(ev);
      if (open) r.m.addMessage(ev, true);
    }
    return ev;
  }
  
  private void putCompleteMessage(MxChatEvent ev) { // called after ev is already in list
    if (threadID!=null && list.sz>=2) {
      MxChatEvent root = r.allKnownEvents.get(threadID);
      if (root!=null && !root.hasThread) {
        root.hasThread = true;
        root.updateExtra();
      }
    }
    msgMap.put(ev.id, ev);
    r.allKnownEvents.put(ev.id, ev);
    if (ev.e0.m!=null && ev.e0.m.replyId!=null) {
      msgReplies.computeIfAbsent(ev.e0.m.replyId, k->new Vec<>(2)).add(ev.id);
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
  
  public String threadDesc(int maxLen) {
    if (maxLen==0) maxLen = Integer.MAX_VALUE;
    if (threadID==null) return "main";
    MxChatEvent root = r.allKnownEvents.get(threadID);
    if (root==null) return "thread";
    String body = root.src;
    if (body.length()>maxLen) body = body.substring(0, maxLen)+"…";
    return body;
  }
  
  public String toString() {
    return "MxLog→"+(threadID==null?"main":threadID);
  }
}
