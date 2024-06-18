package chat.networkLog;

import chat.ChatMain;
import dzaima.utils.*;
import libMx.MxServer;

import java.time.Instant;
import java.util.concurrent.*;

public class NetworkLog {
  public static ConcurrentHashMap<MxServer.RunnableRequest, RequestStatus> requestMap = new ConcurrentHashMap<>();
  public static ConcurrentLinkedQueue<RequestStatus> requestList = new ConcurrentLinkedQueue<>();
  public static boolean detailed;
  
  public static void open(ChatMain m) {
    
  }
  
  public static void start(boolean detailed) {
    NetworkLog.detailed = detailed;
    MxServer.requestLogger = (s, rq) -> {
      RequestStatus st = new RequestStatus(Instant.now(), s, rq);
      requestMap.put(rq, st);
      requestList.add(st);
    };
    MxServer.requestStatusLogger = (rq, type, o) -> {
      Instant when = Instant.now();
      RequestStatus st = requestMap.get(rq);
      if (st==null) { Log.warn("", "unknown request?"); return; }
      st.events.add(new Event(when, type, o));
    };
  }
  
  public static class RequestStatus {
    public final Instant start;
    public final MxServer s;
    public final MxServer.RunnableRequest rq;
    public final Vec<Event> events = new Vec<>(); 
    
    public RequestStatus(Instant start, MxServer s, MxServer.RunnableRequest rq) {
      this.s = s;
      this.rq = rq;
      this.start = start;
    }
  }
  public static class Event {
    public final Instant when;
    public final String type;
    public final Object obj;
    
    public Event(Instant when, String type, Object obj) {
      this.when = when;
      this.type = type;
      this.obj = obj;
    }
  }
}
