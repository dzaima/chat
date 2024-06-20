package libMx;

import dzaima.utils.JSON;
import dzaima.utils.JSON.Obj;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.time.Instant;

public final class MxMessage {
  public static boolean supportThreads = true;
  public final MxRoom r;
  public final Obj o;
  public final Obj ct;
  public final String type; // "deleted" if this is a redaction
  public final Instant time;
  
  public final String id; // event ID
  public final String uid; // sender
  public final String editsId; // null if none
  public final MxFmted fmt;
  public final MxFmted latestFmt; // if this event has info on the latest edit
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
    String replyId = null;
    Obj rel = ct.obj("m.relates_to", Obj.E);
    String relType = rel.str("rel_type", null);
    
    if ("m.replace".equals(relType)) {
      editsId = rel.str("event_id");
      if (ct.has("m.new_content")) fmtT = new MxFmted(ct.obj("m.new_content"));
    } else {
      if (rel.has("m.in_reply_to") && !(supportThreads && rel.bool("is_falling_back", false))) {
        replyId = rel.obj("m.in_reply_to").str("event_id");
      }
    }
    this.replyId = replyId;
    this.editsId = editsId;
    this.fmt = removeFallbackReply(fmtT);
    
    if (supportThreads && "m.thread".equals(relType)) threadId = rel.str("event_id");
    else threadId = null;
    
    Obj ct2 = Obj.objPath(o, null, "unsigned", "m.relations", "m.replace", "content");
    if (ct2!=null) {
      if (ct2.has("m.new_content")) ct2 = ct2.obj("m.new_content");
      latestFmt = removeFallbackReply(new MxFmted(ct2));
    } else {
      latestFmt = null;
    }
  }
  
  private static MxFmted removeFallbackReply(MxFmted fmtT) {
    if (!fmtT.html.startsWith("<mx-reply>")) return fmtT;
    Document d = Jsoup.parse(fmtT.html);
    d.getElementsByTag("mx-reply").remove();
    d.outputSettings().prettyPrint(false);
    fmtT = new MxFmted(fmtT.body, d.body().html());
    return fmtT;
  }
  
  public boolean isEditEvent() {
    return editsId!=null;
  }
  
  private MxMessage edits;
  public MxMessage loadEditBase() {
    if (!isEditEvent()) return null;
    if (edits == null) {
      edits = r.loadMessage(editsId);
      if (edits.isEditEvent()) edits = edits.loadEditBase();
    }
    return edits;
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
