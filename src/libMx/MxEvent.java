package libMx;

import dzaima.utils.JSON;
import dzaima.utils.JSON.Obj;

import java.time.Instant;

public class MxEvent {
  public final MxRoom r;
  
  public final Obj o, ct;
  public final String type;
  public final String id; // own ID
  public final String uid; // sender
  public final Instant time;
  
  public final MxMessage m; // this or null
  
  protected MxEvent(MxRoom r, Obj o, String defaultID) {
    this.r = r;
    this.o = o;
    this.type = o.str("type", "");
    this.uid = o.str("sender", "");
    this.id = o.str("event_id", defaultID);
    this.ct = o.obj("content", Obj.E);
    this.time = Instant.ofEpochMilli(o.get("origin_server_ts", JSON.NULL).asLong(0));
    this.m = this instanceof MxMessage? (MxMessage) this : null;
  }
  
  public static MxEvent from(MxRoom r, Obj o) {
    return from(r, o, "");
  }
  public static MxEvent from(MxRoom r, Obj o, String defaultID) {
    if (o.str("type", "").equals("m.room.message")) {
      return new MxMessage(r, o, defaultID);
    } else {
      return new MxEvent(r, o, defaultID);
    }
  }
}