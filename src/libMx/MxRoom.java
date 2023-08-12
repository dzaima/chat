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
    return new MxMessage(this, s.getJ("_matrix/client/r0/rooms/"+rid+"/event/"+id+"?access_token="+s.gToken));
  }
  
  public ArrayList<MxEvent> beforeMsg(String id, int am) {
    Obj o = s.getJ("_matrix/client/r0/rooms/"+rid+"/context/"+id+"?limit="+am+"&access_token="+s.gToken);
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
    Obj o = s.getJ("_matrix/client/r0/rooms/"+rid+"/messages?limit="+am+"&from="+from+(to==null?"":"&to="+to)+"&dir="+d+"&access_token="+s.gToken);
    ArrayList<MxEvent> res = new ArrayList<>();
    if (!o.has("chunk")) return null;
    for (Obj c : o.arr("chunk").objs()) {
      res.add(new MxEvent(this, c));
    }
    if (d=='b') Collections.reverse(res);
    return new Chunk(res, o.str("start"), o.str("end", null));
  }
  
  public Chunk msgContext(String id, int am) {
    Obj o = s.getJ("_matrix/client/r0/rooms/"+rid+"/context/"+id+"?limit="+am+"&access_token="+s.gToken);
    ArrayList<MxEvent> res = new ArrayList<>();
    if (!o.has("events_before")) return null;
    for (Obj c : o.arr("events_before").objs()) res.add(new MxEvent(this, c));
    Collections.reverse(res);
    if (o.has("event")) res.add(new MxEvent(this, o.obj("event")));
    for (Obj c : o.arr("events_after").objs()) res.add(new MxEvent(this, c));
    return new Chunk(res, o.str("start", ""), o.str("end", ""));
  }
  
  public void readTo(String id) {
    s.postJ("_matrix/client/r0/rooms/"+rid+"/receipt/m.read/"+id+"?access_token="+s.gToken, "{}");
  }
  
  public void kick(String uid, String reason) { kickBan("kick", uid, reason); }
  public void ban(String uid, String reason) { kickBan("ban", uid, reason); }
  public void unban(String uid) { kickBan("unban", uid, null); }
  
  void kickBan(String mode, String uid, String reason) {
    HashMap<String, JSON.Val> map = new HashMap<>();
    map.put("user_id", new JSON.Str(uid));
    if (reason!=null) map.put("reason", new JSON.Str(reason));
    s.postJ("_matrix/client/v3/rooms/"+rid+"/"+mode+"?access_token="+s.gToken, new JSON.Obj(map).toString());
  }
  
  public String link() {
    return "https://matrix.to/#/"+rid;
  }
  public String linkMsg(String mid) {
    return link()+"/"+mid;
  }
  
  
  public void selfJoin() {
    s.postJ("_matrix/client/v3/join/"+rid+"?access_token="+s.gToken, "{}");
  }
  public void selfRejectInvite() {
    selfLeave();
  }
  public void selfLeave() {
    s.postJ("_matrix/client/v3/rooms/"+rid+"/leave"+"?access_token="+s.gToken, "{}");
  }
  public void selfForget() {
    s.postJ("_matrix/client/v3/rooms/"+rid+"/forget"+"?access_token="+s.gToken, "{}");
  }
}
