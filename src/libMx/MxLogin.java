package libMx;

import java.util.concurrent.atomic.AtomicLong;

import static dzaima.utils.JSON.*;

public class MxLogin {
  public final MxServer s;
  public final String uid;
  public final String token;
  
  public MxLogin(MxServer s, String id, String mxToken) {
    this.s = s;
    this.uid = id;
    this.token = mxToken;
  }
  
  public boolean valid() {
    return !s.requestV3("account","whoami").token(token).get().runJ().has("errcode");
  }
  
  public MxUser user() {
    return s.user(uid);
  }
  
  private static final AtomicLong txn_id = new AtomicLong();
  public static String newTxnId() {
    return (System.currentTimeMillis()%(long)1e12) + "_" + txn_id.getAndIncrement();
  } 
  
  public String sendContent(MxRoom r, String type, String content) {
    Obj j = r.request("send",type,newTxnId()).token(token).put(content).runJ();
    if (s.handleError(j, "send message")) return null;
    return j.str("event_id");
  }
  public String sendMessage(MxRoom r, MxSendMsg msg) {
    return sendContent(r, "m.room.message", msg.msgJSON());
  }
  public String editMessage(MxRoom r, String pid, MxFmt msg) { // ignores msg reply as reply target cannot be edited
    return sendContent(r, "m.room.message", msg.editJSON(pid));
  }
  public void deleteMessage(MxRoom r, String pid) {
    Obj j = r.request("redact",pid,newTxnId()).token(token).put(Obj.E).runJ();
    s.handleError(j, "delete message");
  }
  
  public String sendUserState(MxRoom r, String type, String data) {
    Obj j = r.request("state", type, uid).token(token).put(data).runJ();
    if (s.handleError(j, "send "+type)) return null;
    return j.str("event_id");
  }
  
  
  public boolean join(MxRoom r) {
    Obj j = r.request("join").token(token).post(Obj.E).runJ();
    return !s.handleError(j, "join room");
  }
  
  private Arr deviceInfo;
  public Arr deviceInfo() {
    if (deviceInfo==null) deviceInfo = s.requestV3("devices").token(token).get().runJ().arr("devices");
    return deviceInfo;
  }
  
  public String device() {
    Arr ds = deviceInfo();
    return ds.size()==0? null : ds.obj(ds.size()-1).str("device_id");
  }
  
  public void setGlobalNick(String nick) {
    Obj j = s.requestV3("profile",uid,"displayname").token(token).put(Obj.fromKV("displayname", nick)).runJ();
    s.handleError(j, "set nick");
  }
  
  public String setRoomNick(MxRoom r, String nick) {
    return sendUserState(r, "m.room.member", Obj.fromKV("membership","join", "displayname",nick).toString());
  }
}
