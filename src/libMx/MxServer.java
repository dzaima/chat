package libMx;

import dzaima.utils.JSON;
import dzaima.utils.JSON.Obj;
import dzaima.utils.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.*;


public class MxServer {
  public static int SYNC_TIMEOUT = 85000;
  public final String url;
  public String gToken;
  public MxLogin primaryLogin;
  
  public MxServer(String url, String id, String gToken) {
    this.url = url;
    setG(new MxLogin(this, id, gToken));
  }
  
  private MxServer(String url) {
    this.url = url;
  }
  
  public void setG(MxLogin l) {
    gToken = l.token;
    primaryLogin = l;
  }
  
  public static MxServer of(MxLoginMgr mgr) {
      MxServer s = new MxServer(mgr.getServer());
      MxLogin l = s.login(mgr);
      if (l==null) return (MxServer) Utils.qnull;
      s.setG(l);
      return s;
  }
  
  public MxLogin login(MxLoginMgr mgr) {
    String token = mgr.getToken();
    if (token!=null) {
      MxLogin l = new MxLogin(this, mgr.getUserID(), mgr.getToken());
      if (l.valid()) return l;
    }
    
    MxLogin l = login(mgr.getUserID(), mgr.getPassword());
    if (l==null) return null;
    
    mgr.updateToken(l.token);
    return l;
  }
  public MxLogin login(String uid, String passwd) {
    hide_data = true;
    try {
      Obj j = requestV3("login").post(Obj.fromKV("type","m.login.password", "user",uid, "password",passwd)).runJ();
      if (j.has("errcode")) {
        warn("failed to log in");
        return null;
      }
      return new MxLogin(this, uid, j.str("access_token"));
    } finally {
      hide_data = false;
    }
  }
  
  
  public class Request {
    private final String[] pathParts;
    private final ArrayList<String> props = new ArrayList<>();
    public Request(String[] pathParts) {
      for (String s : pathParts) if (s.indexOf('/')!=-1) throw new IllegalStateException("'/' in URL path segment");
      this.pathParts = pathParts;
    }
    
    public Request prop(String key, String value) {
      if (value.indexOf('&')!=-1) throw new IllegalStateException("'&' in property value");
      props.add(key+"="+value);
      return this;
    }
    public Request prop(String key, Number value) {
      return prop(key, value.toString());
    }
    public Request optProp(String key, String value) {
      if (value!=null) prop(key, value);
      return this;
    }
    public Request token(String token) {
      prop("access_token", token);
      return this;
    }
    public Request gToken() {
      return token(gToken);
    }
    
    private RunnableRequest type(RequestType n, String ct) {
      return new RunnableRequest(this, n, ct);
    }
    public RunnableRequest get() { return type(RequestType.GET, null); }
    public RunnableRequest  put(String content) { return type(RequestType.PUT, content); }
    public RunnableRequest post(String content) { return type(RequestType.POST, content); }
    public RunnableRequest  put(Obj o) { return put(o.toString()); }
    public RunnableRequest post(Obj o) { return post(o.toString()); }
    
  }
  public class RunnableRequest {
    public final Request r;
    public final RequestType t;
    public final String ct;
    
    public RunnableRequest(Request r, RequestType t, String ct) {
      this.r = r;
      this.t = t;
      this.ct = ct;
    }
    
    public <T> T tryRun(Function<String, Pair<T, Integer>> get) {
      if (t==null) throw new IllegalStateException("Request type not set");
      StringBuilder p = new StringBuilder();
      for (int i = 0; i < r.pathParts.length; i++) {
        if (i!=0) p.append('/');
        p.append(r.pathParts[i]);
      }
      for (int i = 0; i < r.props.size(); i++) {
        p.append(i==0? '?' : '&');
        p.append(r.props.get(i));
      }
      String path = p.toString();
      
      int expTime = 1000;
      while (true) {
        int retryTime = 0;
        try {
          log(t.name(), path, ct);
          String res;
          switch (t) { default: throw new IllegalStateException();
            case GET:  res = Utils.get (url+"/"+path); break;
            case PUT:  res = Utils.put (url+"/"+path, ct.getBytes(StandardCharsets.UTF_8)); break;
            case POST: res = Utils.post(url+"/"+path, ct.getBytes(StandardCharsets.UTF_8)); break;
          }
          Pair<T, Integer> r = get.apply(res);
          if (r.b==null) return r.a;
          retryTime = r.b;
        } catch (RuntimeException e) {
          warn("Failed to parse result:");
          warnStacktrace(e);
        }
        
        retryTime = Math.max(retryTime, expTime);
        log("mxq", "Retrying in "+(retryTime/1000)+"s");
        Utils.sleep(retryTime);
        expTime = Math.min(Math.max(expTime*2, 1000), 180*1000);
      }
    }
    
    public String runStr() {
      return tryRun(s -> new Pair<>(s, null));
    }
    public Obj runJ() {
      return tryRun((s) -> {
        Obj r;
        try {
          r = JSON.parseObj(s);
        } catch (Throwable e) {
          warn("Failed to parse JSON");
          warnStacktrace(e);
          r = null;
        }
        if (r!=null && !"M_LIMIT_EXCEEDED".equals(r.str("errcode", null))) return new Pair<>(r, null);
        if (r!=null && r.hasNum("retry_after_ms")) return new Pair<>(r, r.getInt("retry_after_ms"));
        return new Pair<>(r, 1000);
      });
    }
  }
  
  public enum RequestType { POST, GET, PUT }
  
  public static String[] concat(String[] a, String[] b) {
    String[] res = new String[a.length+b.length];
    System.arraycopy(a, 0, res, 0, a.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }
  public Request requestV3(String... path) {
    return new Request(concat(new String[]{"_matrix","client","v3"}, path));
  }
  
  public byte[] getB(String path) {
    log("GET bytes", path, null);
    return Utils.getB(url+"/"+path);
  }
  private HashMap<String, byte[]> getBCache;
  public byte[] getBCached(String path) {
    if (getBCache==null) getBCache = new HashMap<>();
    byte[] prev = getBCache.get(path);
    if (prev!=null) return prev;
    byte[] curr = getB(path);
    getBCache.put(path, curr);
    return curr;
  }
  
  private final HashMap<String, MxRoom> rooms = new HashMap<>();
  public MxRoom room(String rid) {
    MxRoom r = rooms.get(rid);
    if (r==null) {
      r = new MxRoom(this, rid);
      rooms.put(rid, r);
    }
    return r;
  }
  
  public MxUser user(String uid) {
    return new MxUser(this, uid);
  }
  
  
  public Obj sync(int count) {
    return requestV3("sync").prop("filter", "{\"room\":{\"timeline\":{\"limit\":"+count+"}}}").gToken().get().runJ();
  }
  public String latestBatch() {
    return sync(1).str("next_batch");
  }
  
  public MxLogin register(String id, String device, String passwd) {
    for (int i = 0; i < id.length(); i++) {
      if (!MxUser.nameChars.contains(String.valueOf(id.charAt(i)))) return null;
    }
    int i = 0;
    while (true) {
      String cid = i==0? id : id+i;
      Obj j = requestV3("register").prop("kind","user").post(Obj.fromKV(
        "username", cid,
        "password", passwd,
        "device_id", device,
        "auth", Obj.fromKV("type", "m.login.dummy")
      )).runJ();
      log("register: "+j.toString());
      String err = j.str("errcode", null);
      if ("M_USER_IN_USE".equals(err)) {
        if (i>20) return null;
        i++;
        Utils.sleep(100);
        continue;
      }
      if (handleError(j, "register")) return null;
      return new MxLogin(this, j.str("user_id"), j.str("access_token"));
    }
  }
  
  
  public Obj messagesSince(String since, int timeout) {
    return requestV3("sync").prop("since",since).prop("timeout",timeout).gToken().get().runJ();
  }
  
  
  
  
  
  
  
  public static void log(String s) {
    log("mx", s);
  }
  private static boolean hide_data = false;
  private void log(String method, String uri, String data) {
    if (uri.startsWith("/")) System.err.println("!!!!!!!!!!!!! STARTING SLASH !!!!!!!!!!!!!");
    String df = data==null? "" : " "+(data.length()>100 || hide_data? "..." : data);
    log("mxq", method+" "+url+"/"+uri.replaceAll("access_token=[^&]+", "access_token=<redacted>")+df);
    // if (!uri.contains("_matrix/client/r0/sync?")) { // don't log sync spam
    //   log("mxq", method+" "+uri.replaceAll("access_token=[^&]+", "access_token=<redacted>")+df);
    // }
  }
  
  public boolean handleError(Obj j, String do_what) {
    if (!j.has("errcode")) return false;
    warn("Failed to "+do_what+": "+j);
    return true;
  }
  
  public static void log(String id, String s) {
    if (LOG) LOG_FN.accept(id, s);
  }
  public static void warn(String s) {
    WARN_FN.accept("mx itf", s);
  }
  public static void warnStacktrace(Throwable t) {
    StringWriter w = new StringWriter();
    t.printStackTrace(new PrintWriter(w));
    WARN_FN.accept("mx itf", w.toString());
  }
  public static boolean LOG = true;
  public static BiConsumer<String, String> LOG_FN = (id, s) -> System.out.println("["+LocalDateTime.now()+" "+id+"] "+s);
  public static BiConsumer<String, String> WARN_FN = (id, s) -> System.err.println("["+LocalDateTime.now()+" !!] "+s);
  
  
  public static boolean isMxc(String uri) {
    return uri.startsWith("mxc://");
  }
  public String mxcPath(String mxc) {
    if (!mxc.startsWith("mxc://")) throw new RuntimeException("not an mxc URL: "+mxc);
    return mxc.substring(6);
  }
  public String mxcToURL(String mxc) {
    return url+"/_matrix/media/r0/download/"+mxcPath(mxc);
  }
  
  public enum ThumbnailMode { CROP("crop"), SCALE("scale"); final String s; ThumbnailMode(String s) { this.s = s; } }
  public String mxcToThumbnailURL(String mxc, int w, int h, ThumbnailMode mode) {
    return url+"/_matrix/media/v3/thumbnail/"+mxcPath(mxc)+"?width="+w+"&height="+h+"&mode="+mode.s;
  }
}
