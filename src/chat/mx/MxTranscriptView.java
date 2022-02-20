package chat.mx;

import chat.*;
import libMx.*;

public class MxTranscriptView extends TranscriptView {
  public final MxChatroom r;
  public final MxLog log;
  private final String highlightID;
  private int highlightTime = 2;
  
  
  public MxTranscriptView(MxChatroom r, String highlightID, MxRoom.Chunk c) {
    this.r = r;
    this.highlightID = highlightID;
    this.log = new MxLog(r);
    tokB = c.sTok;
    tokF = c.eTok;
    log.addEvents(c.events, true);
  }
  
  
  public void tick() {
    if (highlightTime>=0) highlightTime--;
    if (highlightTime==0) {
      MxChatEvent m = log.get(highlightID);
      if (m!=null) m.highlight(true);
    }
  }
  
  public void show() { super.show(); log.show(); }
  public void hide() { super.hide(); log.hide(); }
  public Chatroom room() { return r; }
  
  public String tokB;
  public String tokF;
  public void older() {
    if (tokB==null) return;
    String tok = tokB;
    tokB = null;
    r.u.queueRequest(null, () -> r.r.beforeTok(tok, 50), r -> {
      if (r.events.size()==0) return;
      log.addEvents(r.events, false);
      tokB = r.eTok;
    });
  }
  public void newer() {
    if (tokF==null) return;
    String tok = tokF;
    tokF = null;
    r.u.queueRequest(null, () -> r.r.afterTok(tok, 50), r -> {
      if (r.events.size()==0) return;
      log.addEvents(r.events, true);
      tokF = r.eTok;
    });
  }
}
