package chat.mx;

import chat.*;
import dzaima.utils.*;
import libMx.MxRoom;

public class MxTranscriptView extends TranscriptView {
  public final MxChatroom r;
  public final MxLog log;
  
  private String highlightID;
  private int highlightTime;
  
  
  public MxTranscriptView(MxChatroom r, MxRoom.Chunk c) {
    this.r = r;
    this.log = new MxLog(r, r.mainLiveView.log.threadID, r.mainLiveView); // TODO thread
    tokB = c.sTok;
    tokF = c.eTok;
    log.addEvents(c.events, true);
  }
  
  public void highlight(ChatEvent ev) {
    this.highlightID = ev.id;
    this.highlightTime = 2;
  }
  
  public void openViewTick() {
    if (highlightTime>=0 && highlightID!=null) {
      highlightTime--;
      MxChatEvent m = log.get(highlightID);
      if (m!=null) m.highlight(true);
    }
    super.openViewTick();
  }
  
  public LiveView baseLiveView() {
    return log.liveView();
  }
  
  public boolean contains(ChatEvent ev) {
    return log.contains(ev);
  }
  
  public void show() { super.show(); log.show(); highlightTime=2; }
  public void hide() { super.hide(); log.hide(); }
  public Chatroom room() { return r; }
  
  public String tokB;
  public String tokF;
  public void older() {
    if (tokB==null) return;
    String tok = tokB;
    tokB = null;
    Log.fine("mx", "Loading older messages in transcript");
    JSON.Obj filter = r.currMemberFilter();
    r.u.queueRequest(() -> r.r.beforeTok(filter, tok, 50), c -> {
      r.loadQuestionableMemberState(c);
      if (c.events.isEmpty()) return;
      log.addEvents(c.events, false);
      tokB = c.eTok;
    });
  }
  public void newer() {
    if (tokF==null) return;
    String tok = tokF;
    tokF = null;
    Log.fine("mx", "Loading newer messages in transcript");
    JSON.Obj filter = r.currMemberFilter();
    r.u.queueRequest(() -> r.r.afterTok(filter, tok, 50), c -> {
      r.loadQuestionableMemberState(c);
      if (c.events.isEmpty()) return;
      log.addEvents(c.events, true);
      tokF = c.eTok;
    });
  }
  
  public View getSearch() {
    return new MxSearchView(r.m, this);
  }
}
