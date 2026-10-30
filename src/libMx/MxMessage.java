package libMx;

import dzaima.utils.JSON.Obj;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class MxMessage extends MxEvent {
  public static boolean supportThreads = true;
  public final String msgType; // "deleted" if this is a redaction
  
  public final String editsId; // null if none
  public final String threadId; // null if none
  public final String replyId; // null if none
  
  public final MxFmted fmt; // primary content in the event with this ID (includes picking out the proper content of edit events, but not tracing through unsigned for latest edit)
  public final Obj latestEditInfo; // if there's unsigned info of the latest edit (i.e. in pagination)
  
  public MxMessage(MxRoom r, Obj o) {
    this(r, o, null);
  }
  MxMessage(MxRoom r, Obj o, String defaultID) {
    super(r, o, defaultID);
    msgType = ct.str("msgtype", "deleted");
    
    Obj fmtCt = ct;
    String editsId = null;
    String replyId = null;
    Obj rel = ct.obj("m.relates_to", Obj.E);
    String relType = rel.str("rel_type", null);
    
    if ("m.replace".equals(relType)) {
      editsId = rel.str("event_id", null);
      if (ct.has("m.new_content")) fmtCt = ct.obj("m.new_content", Obj.E);
    } else {
      if (rel.has("m.in_reply_to") && !(supportThreads && rel.bool("is_falling_back", false))) {
        replyId = rel.obj("m.in_reply_to").str("event_id");
      }
    }
    this.replyId = replyId;
    this.editsId = editsId;
    this.fmt = removeFallbackReply(new MxFmted(fmtCt));
    if (supportThreads && "m.thread".equals(relType)) threadId = rel.str("event_id", "");
    else threadId = null;
    
    latestEditInfo = Obj.objPath(o, null, "unsigned", "m.relations", "m.replace");
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
  
  public boolean equals(Object o) {
    if (!(o instanceof MxMessage)) return false;
    return ((MxMessage) o).id.equals(id);
  }
  
  public int hashCode() {
    return id.hashCode();
  }
}
