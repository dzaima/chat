package libMx;

import dzaima.utils.JSON;

import java.util.*;

import static dzaima.utils.JSON.Obj;

public class MxRoom {
  public final MxServer s;
  public final String rid;
  
  public MxRoom(MxServer s, String rid) {
    this.s = s;
    this.rid = rid;
  }
  
  
  public MxMessage message(String id) {
    return new MxMessage(this, request("event",id).gToken().get().runJ());
  }
  
  public ArrayList<MxEvent> beforeMsg(String id, int am) {
    Obj o = request("context",id).prop("limit",am).gToken().get().runJ();
    ArrayList<MxEvent> res = new ArrayList<>();
    if (!o.has("events_before")) return null;
    for (Obj c : o.arr("events_before").objs()) {
      res.add(new MxEvent(this, c));
    }
    Collections.reverse(res);
    return res;
  }
  
  public MxServer.Request request(String... path) {
    return s.requestV3(MxServer.concat(new String[]{"rooms",rid}, path));
  }
  
  public static class Chunk {
    public final ArrayList<MxEvent> events;
    public final String sTok;
    public final String eTok; // token for next batch
    
    public Chunk(ArrayList<MxEvent> events, String sTok, String eTok) { this.events = events; this.sTok = sTok; this.eTok = eTok; }
  }
  public Chunk beforeTok(String from, int am) { return beforeTok(from, null, am); }
  public Chunk afterTok(String from, int am) { return getMessages(from, null, 'f', am); }
  public Chunk beforeTok(String from, String to, int am) { return getMessages(from, to, 'b', am); }
  public Chunk getMessages(String from, String to, char d, int am) {
    Obj o = request("messages").prop("limit",am).prop("from",from).prop("dir",String.valueOf(d)).optProp("to",to).gToken().get().runJ();
    ArrayList<MxEvent> res = new ArrayList<>();
    if (!o.has("chunk")) return null;
    for (Obj c : o.arr("chunk").objs()) {
      res.add(new MxEvent(this, c));
    }
    if (d=='b') Collections.reverse(res);
    return new Chunk(res, o.str("start"), o.str("end", null));
  }
  
  public Chunk msgContext(String id, int am) {
    Obj o = request("context",id).prop("limit", am).gToken().get().runJ();
    ArrayList<MxEvent> res = new ArrayList<>();
    if (!o.has("events_before")) return null;
    for (Obj c : o.arr("events_before").objs()) res.add(new MxEvent(this, c));
    Collections.reverse(res);
    if (o.has("event")) res.add(new MxEvent(this, o.obj("event")));
    for (Obj c : o.arr("events_after").objs()) res.add(new MxEvent(this, c));
    return new Chunk(res, o.str("start", ""), o.str("end", ""));
  }
  
  public JSON.Arr getFullMemberState(String token) {
    Obj o = request("members").gToken().prop("at", token).get().runJ();
    return o.has("chunk")? o.arr("chunk") : null;
  }
  
  public void readTo(String id) {
    request("receipt","m.read",id).gToken().post(Obj.E).runJ();
  }
  
  public String sendState(String type, String data) {
    Obj j = request("state", type).gToken().put(data).runJ();
    if (s.handleError(j, "send "+type)) return null;
    return j.str("event_id");
  }
  
  public String setRoomName(String name) {
    return sendState("m.room.name", Obj.fromKV("name", name).toString());
  }
  
  public void kick(String uid, String reason) { kickBan("kick", uid, reason); }
  public void ban(String uid, String reason) { kickBan("ban", uid, reason); }
  public void unban(String uid) { kickBan("unban", uid, null); }
  
  void kickBan(String mode, String uid, String reason) {
    HashMap<String, JSON.Val> map = new HashMap<>();
    map.put("user_id", new JSON.Str(uid));
    if (reason!=null) map.put("reason", new JSON.Str(reason));
    request(mode).gToken().post(new Obj(map)).runJ();
  }
  
  public String link() {
    return "https://matrix.to/#/"+rid;
  }
  public String linkMsg(String mid) {
    return link()+"/"+mid;
  }
  
  
  public void selfJoin() {
    s.primaryLogin.join(this);
  }
  public void selfRejectInvite() {
    selfLeave();
  }
  public boolean selfLeave() {
    Obj j = request("leave").gToken().post(Obj.E).runJ();
    return !s.handleError(j, "leave room");
  }
  public boolean selfForget() {
    Obj j = request("forget").gToken().post(Obj.E).runJ();
    return !s.handleError(j, "forget room");
  }
}
