package libMx;

import dzaima.utils.JSON;
import dzaima.utils.JSON.Obj;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.time.Instant;

public final class MxMessage {
  public final MxRoom r;
  public final Obj o;
  public final Obj ct;
  public final String type; // "deleted" if this is a redaction
  public final Instant time;
  
  public final String id;
  public final String uid;
  public final String editsId; // null if none
  public final int edit; // 0 - not edited; 1 - informing about edit; 2 - full edited message
  public final MxFmted fmt;
  public final String threadId; // null if none
  public final String replyId; // null if none
  public MxMessage(MxRoom r, Obj o) { // TODO do sane things for evil inputs
    this.r = r;
    this.o = o;
    uid = o.str("sender");
    id = o.str("event_id");
    time = Instant.ofEpochMilli(o.get("origin_server_ts", JSON.NULL).asLong(0));
    ct = o.obj("content");
    type = ct.str("msgtype", "deleted");
    
    MxFmted fmtT = new MxFmted(ct);
    String editsId = null;
    int edit = 0;
    String replyId = null;
    Obj rel = ct.obj("m.relates_to", Obj.E);
    String relType = rel.str("rel_type", null);
    if ("m.replace".equals(relType)) {
      editsId = rel.str("event_id");
      edit = 1;
      if (ct.has("m.new_content")) fmtT = new MxFmted(ct.obj("m.new_content"));
    } else {
      if (rel.has("m.in_reply_to") && !rel.bool("is_falling_back", false)) {
        replyId = rel.obj("m.in_reply_to").str("event_id");
      }
    }
    if ("m.thread".equals(relType)) threadId = rel.str("event_id");
    
    else threadId = null;
    if (fmtT.html.startsWith("<mx-reply>")) {
      Document d = Jsoup.parse(fmtT.html);
      d.getElementsByTag("mx-reply").remove();
      d.outputSettings().prettyPrint(false);
      fmtT = new MxFmted(fmtT.body, d.body().html());
    }
    this.replyId = replyId;
    this.fmt = fmtT;
    if (editsId==null) {
      Obj rels = Obj.objPath(o, Obj.E, "unsigned", "m.relations");
      if (rels.has("m.replace")) {
        // i have no clue wtf this is
        // editsId = rel.getObj("m.replace").str("event_id");
        edit = 2;
      }
    }
    this.editsId = editsId;
    this.edit = edit;
  }
  
  private MxMessage edits;
  public MxMessage edits() {
    if (edit!=1) return null;
    if (edits == null) edits = r.message(editsId);
    return edits;
  }
  
  private boolean gotReply;
  private MxMessage reply;
  public MxMessage reply() {
    if (!gotReply) {
      MxMessage c = this;
      //noinspection ConstantConditions
      while(c.edit==1) c = c.edits();
      String id = Obj.objPath(c.ct, Obj.E, "m.relates_to", "m.in_reply_to").str("event_id", null);
      try {
        if (id!=null) reply = r.message(id);
      } catch (Exception e) { MxServer.warn("Bad reply "+id); }
      gotReply = true;
    }
    return reply;
  }
  
  public boolean equals(Object o) {
    if (!(o instanceof MxMessage)) return false;
    return ((MxMessage) o).id.equals(id);
  }
  
  public int hashCode() {
    return id.hashCode();
  }
  
  public MxEvent fakeEvent() {
    return new MxEvent(this);
  }
}
