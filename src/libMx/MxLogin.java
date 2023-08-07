package libMx;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static dzaima.utils.JSON.*;

public class MxLogin {
  public MxServer s;
  public final String uid;
  public final String uidURI;
  public String token;
  
  public MxLogin(MxServer s, String id, String mxToken) {
    this.s = s;
    this.uid = id;
    this.token = mxToken;
    uidURI = Utils.toURI(uid);
  }
  
  public boolean valid() {
    return !s.getJ("_matrix/client/r0/account/whoami?access_token="+token).has("errcode");
  }
  
  public MxUser user() {
    return s.user(uid);
  }
  
  private static final AtomicLong txn_id = new AtomicLong();
  public static String newTxnId() {
    return (System.currentTimeMillis()%(long)1e12) + "_" + txn_id.getAndIncrement();
  } 
  
  public String sendContent(MxRoom r, String type, String content) {
    Obj j = s.putJ("_matrix/client/v3/rooms/"+r.rid+"/send/"+type+"/"+newTxnId()+"?access_token="+token, content);
    if (s.handleError(j, "send message")) return null;
    return j.str("event_id");
  }
  public String sendMessage(MxRoom r, MxSendMsg msg) {
    return sendContent(r, "m.room.message", msg.msgJSON());
  }
  public String editMessage(MxRoom r, String pid, MxFmt msg) { // ignores msg reply as reply target cannot be edited
    return sendContent(r, "m.room.message", msg.editJSON(pid));
  }
  private static final AtomicLong txn = new AtomicLong(ThreadLocalRandom.current().nextLong());
  public void deleteMessage(MxRoom r, String pid) {
    Obj j = s.putJ("_matrix/client/r0/rooms/"+r.rid+"/redact/"+pid+"/"+txn.getAndIncrement()+"?access_token="+token, "{}");
    s.handleError(j, "delete message");
  }
  
  public String event(MxRoom r, String type, String data) {
    Obj j = s.putJ("_matrix/client/r0/rooms/"+r.rid+"/state/"+type+"?access_token="+token, data);
    if (s.handleError(j, "send "+type)) return null;
    return j.str("event_id");
  }
  
  
  public boolean join(MxRoom r) {
    Obj j = s.postJ("_matrix/client/r0/rooms/"+r.rid+"/join?access_token="+token, "{}");
    return !s.handleError(j, "join room");
  }
  
  private Arr deviceInfo;
  public Arr deviceInfo() {
    if (deviceInfo==null) deviceInfo = s.getJ("_matrix/client/r0/devices?access_token="+token).arr("devices");
    return deviceInfo;
  }
  
  public String device() {
    Arr ds = deviceInfo();
    return ds.size()==0? null : ds.obj(ds.size()-1).str("device_id");
  }
  
  public void nick(String nick) {
    Obj j = s.putJ("_matrix/client/r0/profile/"+uidURI+"/displayname?access_token="+token, "{\"displayname\":"+Utils.toJSON(nick)+"}");
    s.handleError(j, "set nick");
  }
  
  public String nick(MxRoom r, String nick) {
    return event(r, "m.room.member/"+uidURI, "{\"membership\":\"join\", \"displayname\":"+Utils.toJSON(nick)+"}");
  }
}
