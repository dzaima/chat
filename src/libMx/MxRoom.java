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
  
  
  public MxMessage loadMessage(String id) {
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
  public MxServer.Request requestV(int v, String... path) {
    return s.requestV(v, MxServer.concat(new String[]{"rooms",rid}, path));
  }
  
  public static class Chunk {
    public final ArrayList<MxEvent> events;
    public final ArrayList<MxEvent> states;
    public final String sTok;
    public final String eTok; // token for next batch
    
    public Chunk(ArrayList<MxEvent> events, ArrayList<MxEvent> states, String sTok, String eTok) { this.events=events; this.states=states; this.sTok=sTok; this.eTok=eTok; }
  }
  public static Obj roomEventFilter(boolean lazyLoadMembers) {
    return Obj.fromKV("lazy_load_members", lazyLoadMembers);
  }
  public Chunk beforeTok(Obj filter, String from, int am) { return beforeTok(filter, from, null, am); }
  public Chunk afterTok(Obj filter, String from, int am) { return getMessages(filter, from, null, 'f', am); }
  public Chunk beforeTok(Obj filter, String from, String to, int am) { return getMessages(filter, from, to, 'b', am); }
  public Chunk getMessages(Obj filter, String from, String to, char d, int am) {
    MxServer.Request r = request("messages");
    r.optProp("filter", filter == null? null : filter.toString());
    return getChunk(r, from, to, d, am, "start", "end");
  }
  private Chunk getChunk(MxServer.Request r, String from, String to, char d, int am, String sTokKey, String eTokKey) {
    if (am!=-1) r.prop("limit", am);
    r.prop("dir", String.valueOf(d));
    r.optProp("from", from);
    r.optProp("to", to);
    Obj o = r.gToken().get().runJ();
    
    ArrayList<MxEvent> events = new ArrayList<>();
    if (!o.has("chunk")) return null;
    for (Obj c : o.arr("chunk").objs()) events.add(new MxEvent(this, c));
    
    
    if (d=='b') Collections.reverse(events);
    return new Chunk(events, readState(o), o.str(sTokKey, null), o.str(eTokKey, null));
  }
  
  public Chunk relationsBeforeTok(String id, String type, String from, int am) { return relationsBeforeTok(id, type, from, null, am); }
  public Chunk relationsBeforeTok(String id, String type, String from, String to, int am) {
    MxServer.Request r = type==null? requestV(1, "relations", id) : requestV(1, "relations", id, type);
    r.prop("org.matrix.msc3981.recurse","true");
    return getChunk(r, from, to, 'b', am, "prev_batch", "next_batch");
  }
  
  public Chunk msgContext(Obj filter, String id, int am) {
    Obj o = request("context",id)
      .optProp("filter", filter==null? null : filter.toString())
      .prop("limit", am)
      .gToken().get().runJ();
    
    ArrayList<MxEvent> events = new ArrayList<>();
    
    if (!o.has("events_before")) return null;
    for (Obj c : o.arr("events_before").objs()) events.add(new MxEvent(this, c));
    Collections.reverse(events);
    
    if (o.has("event")) events.add(new MxEvent(this, o.obj("event")));
    
    for (Obj c : o.arr("events_after").objs()) events.add(new MxEvent(this, c));
    
    return new Chunk(events, readState(o), o.str("start", ""), o.str("end", ""));
  }
  
  public Obj getState(String type, String key) {
    Obj r = request("state", type, key).gToken().get().runJ();
    return r.has("membership")? r : null;
  }
  public MxEvent getMemberState(String uid) {
    Obj ct = getState("m.room.member", uid);
    return ct==null? null : new MxEvent(this, Obj.fromKV("content", ct, "state_key", uid));
  }
  
  private ArrayList<MxEvent> readState(Obj o) {
    ArrayList<MxEvent> states = new ArrayList<>();
    for (Obj c : o.arr("state", JSON.Arr.E).objs()) states.add(new MxEvent(this, c));
    return states;
  }
  
  public JSON.Arr getFullMemberState(String token) {
    Obj o = request("members").gToken().prop("at", token).get().runJ();
    return o.has("chunk")? o.arr("chunk") : null;
  }
  
  public void readTo(String id, String threadID) { // null for main
    readToImpl(id, Obj.fromKV("thread_id", threadID==null? "main" : threadID));
  }
  public void readToUnthreaded(String id) {
    readToImpl(id, Obj.E);
  }
  
  private void readToImpl(String id, Obj z) {
    request("receipt","m.read", id).gToken().post(z).runJ();
  }
  
  public String sendState(String type, String data) {
    Obj j = request("state", type).gToken().put(data).runJ();
    if (s.handleError(j, "send "+type)) return null;
    return j.str("event_id");
  }
  
  public String setRoomName(String name) {
    return sendState("m.room.name", Obj.fromKV("name", name).toString());
  }
  
  public void kick(String uid, String reason) { onUser("kick", uid, reason); }
  public void ban(String uid, String reason) { onUser("ban", uid, reason); }
  public void invite(String uid, String reason) { onUser("invite", uid, reason); }
  public void unban(String uid) { onUser("unban", uid, null); }
  
  void onUser(String mode, String uid, String reason) {
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
