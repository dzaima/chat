package libMx;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static dzaima.utils.JSON.*;

public class MxSync {
  public final MxServer s;
  private final ConcurrentLinkedQueue<MxEvent> recv = new ConcurrentLinkedQueue<>();
  
  public MxSync(MxServer s, String since) {
    this(s, s.messagesSince(null, since, 0));
  }
  
  MxSync(MxServer s, Obj prev) {
    this.s = s;
    update(prev);
    stoppedBatchToken = prev.str("next_batch");
  }
  
  public MxSync(MxRoom r, String since) {
    this(r.s, r.s.messagesSince(null, since, 0));
  }
  
  void update(Obj upd) {
    Obj join = Obj.objPath(upd, Obj.E, "rooms", "join");
    for (Entry e : join.entries()) {
      MxRoom r = s.room(e.k);
      for (Obj evo : Obj.arrPath(e.v, Arr.E, "timeline", "events").objs()) {
        recv.add(new MxEvent(r, evo));
      }
    }
  }
  
  
  private final AtomicBoolean running = new AtomicBoolean(false);
  private String stoppedBatchToken;
  public void start() {
    if (!running.compareAndSet(false, true)) throw new RuntimeException("Cannot start a started MxSync");
    Utils.thread(() -> {
      MxServer.log("Sync started");
      String batch = stoppedBatchToken;
      stoppedBatchToken = null;
      int failTime = 16;
      while (running.get()) {
        try {
          Obj c = s.messagesSince(null, batch, MxServer.SYNC_TIMEOUT);
          update(c);
          batch = c.str("next_batch");
          failTime = 16;
        } catch (Throwable t) {
          failTime = Math.min(2*failTime, 180);
          Utils.warn("Failed to update:");
          Utils.warnStacktrace(t);
          Utils.warn("Retrying in "+(failTime)+"s");
          Utils.sleep(failTime*1000);
        }
        Utils.sleep(100);
      }
      stoppedBatchToken = batch;
    }, true);
  }
  public void stop() {
    if (!running.compareAndSet(true, false)) throw new RuntimeException("Cannot stop a stopped MxSync");
  }
  public MxEvent poll() {
    assert running.get();
    return recv.poll();
  }
  public MxEvent next() {
    assert running.get();
    while (true) {
      MxEvent res = recv.poll();
      if (res!=null) return res;
      Utils.sleep(100);
    }
  }
}
