package libMx;

import dzaima.utils.JSON;
import dzaima.utils.JSON.Obj;

import java.util.HashMap;

public abstract class MxSendMsg {
  protected String replyID;
  protected String threadID;
  
  protected void replyToBase(String mid) {
    assert replyID==null;
    replyID = mid;
  }
  
  public abstract void replyTo(MxRoom r, String mid);
  
  public void inThread(String mid) {
    assert threadID==null;
    threadID = mid;
  }
  
  protected void addReplyContent(Obj ct) {
    if (replyID == null && threadID == null) return;
    Obj rel = Obj.fromKV("m.in_reply_to", Obj.fromKV("event_id", replyID!=null? replyID : threadID));
    if (threadID!=null) {
      rel.put("rel_type", new JSON.Str("m.thread"));
      rel.put("event_id", new JSON.Str(threadID));
      rel.put("is_falling_back", JSON.Bool.of(replyID==null));
    }
    ct.put("m.relates_to", rel);
  }
  
  public abstract String msgJSON();
  
  public static MxSendMsg file(String url, String body, String mime, int size) {
    return new MxJSONMsg(Obj.fromKV(
      "msgtype", "m.file",
      "body", body,
      "url", url,
      "info", Obj.fromKV(
        "mimetype", mime,
        "size", size
      )
    ));
  }
  public static MxSendMsg image(String url, String body, String mime, int size, int w, int h) {
    Obj info = new Obj(new HashMap<>());
    if (size!=-1) info.put("size", new JSON.Num(size));
    if (w   !=-1) info.put("w",    new JSON.Num(w));
    if (h   !=-1) info.put("h",    new JSON.Num(h));
    info.put("mimetype", new JSON.Str(mime));
    Obj ct = Obj.fromKV(
      "msgtype", "m.image",
      "body", body,
      "url", url,
      "info", info
    );
    return new MxJSONMsg(ct);
  }
  
  public static MxSendMsg specialFile(String url, String body, String mime, int size, String msgtype) {
    Obj info = new Obj(new HashMap<>());
    if (size!=-1) info.put("size", new JSON.Num(size));
    info.put("mimetype", new JSON.Str(mime));
    Obj ct = Obj.fromKV(
      "msgtype", msgtype,
      "body", body,
      "url", url,
      "info", info
    );
    return new MxJSONMsg(ct);
  }
  
  private static class MxJSONMsg extends MxSendMsg {
    private final Obj ct;
    private MxJSONMsg(Obj ct) { this.ct = ct; }
    
    public void replyTo(MxRoom r, String mid) {
      replyToBase(mid);
    }
    
    public String msgJSON() {
      addReplyContent(ct);
      return ct.toString();
    }
  }
}
