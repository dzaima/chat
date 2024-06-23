package libMx;

import dzaima.utils.JSON;
import dzaima.utils.JSON.Obj;

import java.time.Instant;

public class MxEvent {
  public final Obj o, ct;
  public final MxRoom r;
  public final String uid;
  public final String id;
  
  public final String type;
  public final Instant time;
  
  public final MxMessage m;
  
  public MxEvent(MxRoom r, Obj o) { this(r, o, ""); }
  
  public MxEvent(MxRoom r, Obj o, String defaultID) {
    this.r = r;
    this.o = o;
    this.type = o.str("type", "");
    this.uid = o.str("sender", "");
    this.id = o.str("event_id", defaultID);
    this.ct = o.obj("content", Obj.E);
    this.time = Instant.ofEpochMilli(o.get("origin_server_ts", JSON.NULL).asLong(0));
    this.m = type.equals("m.room.message")? new MxMessage(r, o) : null;
  }
  public MxEvent(MxMessage m) { // fake event
    o = m.o;
    r = m.r;
    uid = m.uid;
    id = m.id;
    ct = m.ct;
    type = m.type;
    time = m.time;
    this.m = m;
  }
}