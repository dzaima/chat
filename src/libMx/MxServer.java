package libMx;

import dzaima.utils.*;
import dzaima.utils.JSON.Obj;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;

import static libMx.Utils.requestLogger;


public class MxServer {
  public static int SYNC_TIMEOUT = 85000;
  public final String url;
  public String gToken;
  public MxLogin primaryLogin;
  
  public HashSet<String> supportedVersions = new HashSet<>();
  public HashSet<String> supportedExperimentalFeatures = new HashSet<>();
  
  public MxServer(String url, String id, String gToken) {
    this.url = url;
    setG(new MxLogin(this, id, gToken));
  }
  
  MxServer(String url) {
    this.url = url;
  }
  
  public void setG(MxLogin l) {
    gToken = l.token;
    primaryLogin = l;
  }
  
  public void loadVersionInfo() {
    Obj o = requestClient("versions").get().runJ();
    if (o==null) return;
    for (String c : o.arr("versions").strs()) supportedVersions.add(c);
    for (JSON.Entry c : o.obj("unstable_features").entries()) if (c.v.equals(JSON.TRUE)) supportedExperimentalFeatures.add(c.k);
  }
  public boolean supportsVersion(String s) {
    return supportedVersions.contains(s);
  }
  
  public MxLogin login(String uid, String passwd) {
    hide_data = true;
    try {
      Obj j = requestV3("login").post(Obj.fromKV("type","m.login.password", "user",uid, "password",passwd)).runJ();
      if (j.has("errcode")) {
        Utils.warn("failed to log in");
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
    private final boolean isDirectUrl;
    private String authorization;
    public Request(String[] pathParts) {
      this.pathParts = pathParts;
      isDirectUrl = false;
    }
    public Request(String directUrl) {
      pathParts = new String[]{directUrl};
      isDirectUrl = true;
    }
    
    public Request prop(String key, String value) {
      if (value.indexOf('&')!=-1) throw new IllegalStateException("'&' in property value");
      props.add(key+"="+Utils.toURI(value));
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
      authorization = "Bearer "+token;
      return this;
    }
    public Request gToken() {
      return token(gToken);
    }
    
    private RunnableRequest type(Utils.RequestType n, String ct, String contentType) {
      return new RunnableRequest(this, n, ct, contentType);
    }
    public RunnableRequest get() { return type(Utils.RequestType.GET, null, null); }
    public RunnableRequest  put(String content, String contentType) { return type(Utils.RequestType.PUT,  content, contentType); }
    public RunnableRequest post(String content, String contentType) { return type(Utils.RequestType.POST, content, contentType); }
    public RunnableRequest  put(String content)                     { return type(Utils.RequestType.PUT,  content, null); }
    public RunnableRequest post(String content)                     { return type(Utils.RequestType.POST, content, null); }
    public RunnableRequest  put(Obj o)                              { return type(Utils.RequestType.PUT,  o.toString(), "application/json"); }
    public RunnableRequest post(Obj o)                              { return type(Utils.RequestType.POST, o.toString(), "application/json"); }
    
    public String calcCurrentPath() {
      if (isDirectUrl) return pathParts[0];
      
      StringBuilder p = new StringBuilder();
      for (int i = 0; i < pathParts.length; i++) {
        if (i!=0) p.append('/');
        p.append(Utils.toURI(pathParts[i]));
      }
      for (int i = 0; i < props.size(); i++) {
        p.append(i==0? '?' : '&');
        p.append(props.get(i));
      }
      return p.toString();
    }
  }
  
  public class RunnableRequest extends Utils.LoggableRequest {
    public final Request r;
    public final String contentType;
    
    public RunnableRequest(Request r, Utils.RequestType t, String ct, String contentType) {
      super(t, ct);
      this.r = r;
      this.contentType = contentType==null? "application/x-www-form-urlencoded" : contentType;
    }
    
    public String calcPath() {
      return r.calcCurrentPath();
    }
    
    public <T> T tryRun(boolean justBytes, Function<Utils.RequestRes, Pair<T, Integer>> get) {
      requestLogger.got(this, "new", MxServer.this);
      
      if (t==null) throw new IllegalStateException("Request type not set");
      String path = calcPath();
      
      int expTime = 1000;
      while (true) {
        int requestedRetry = 0;
        try {
          log(t.name(), path, ct);
          requestLogger.got(this, "start", null);
          Utils.RequestRes res;
          String finalUrl = r.isDirectUrl? path : url+"/"+path;
          Utils.RequestParams p = new Utils.RequestParams(r.authorization);
          switch (t) { default: throw new IllegalStateException();
            case GET:  res = Utils.get (p, finalUrl); break;
            case PUT:  res = Utils.put (p, finalUrl, ct.getBytes(StandardCharsets.UTF_8), contentType); break;
            case POST: res = Utils.post(p, finalUrl, ct.getBytes(StandardCharsets.UTF_8), contentType); break;
          }
          requestLogger.got(this, "status code", res.code);
          if (!justBytes) requestLogger.got(this, "raw result", new String(res.bytes, StandardCharsets.UTF_8));
          // if (Math.random()>0.5) throw new RuntimeException("random error");
          // Tools.sleep((int) (Math.random()*1000));
          
          Pair<T, Integer> r = get.apply(res);
          if (r.b==null) {
            requestLogger.got(this, "result", r.a);
            return r.a;
          }
          requestedRetry = r.b;
        } catch (RuntimeException e) {
          Utils.warn("Failed to parse result:");
          Utils.warnStacktrace(e);
          requestLogger.got(this, "exception", e);
        }
        
        requestedRetry = Math.max(requestedRetry, expTime);
        requestLogger.got(this, "retry", "in "+requestedRetry+"ms");
        Utils.log("mxq", "Retrying in "+(requestedRetry/1000)+"s");
        Utils.sleep(requestedRetry);
        expTime = Math.min(Math.max(expTime*2, 1000), 180*1000);
      }
    }
    
    public Utils.RequestRes runBytesOpt() {
      return tryRun(true, b -> new Pair<>(b, null));
    }
    public String runStr() {
      return tryRun(false, b -> new Pair<>(new String(b.bytes, StandardCharsets.UTF_8), null));
    }
    public Obj runJ() {
      Obj res = tryRun(false, b -> {
        Obj r;
        try {
          r = JSON.parseObj(new String(b.bytes, StandardCharsets.UTF_8));
        } catch (Throwable e) {
          Utils.warn("Failed to parse JSON");
          Utils.warnStacktrace(e);
          requestLogger.got(this, "exception", e);
          r = null;
        }
        if (r!=null && !"M_LIMIT_EXCEEDED".equals(r.str("errcode", null))) return new Pair<>(r, null);
        if (r!=null && r.hasNum("retry_after_ms")) return new Pair<>(r, r.getInt("retry_after_ms"));
        return new Pair<>(r, 1000);
      });
      if (res!=null && res.has("errcode")) requestLogger.got(this, "error", null);
      return res;
    }
  }
  
  public static String[] concat(String[] a, String[] b) {
    String[] res = new String[a.length+b.length];
    System.arraycopy(a, 0, res, 0, a.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }
  
  public Request requestDirectUrl(String path) {
    return new Request(path);
  }
  public Request requestRaw(String... path) {
    return new Request(concat(new String[]{"_matrix"}, path));
  }
  public Request requestClient(String... path) {
    return new Request(concat(new String[]{"_matrix","client"}, path));
  }
  public Request requestV(int v, String... path) {
    for (String s : path) if (s.indexOf('/')!=-1) throw new IllegalStateException("'/' in URL path segment");
    return new Request(concat(new String[]{"_matrix","client","v"+v}, path));
  }
  public Request requestV3(String... path) {
    return requestV(3, path);
  }
  
  private final HashMap<String, MxRoom> rooms = new HashMap<>();
  public MxRoom room(String rid) {
    return rooms.computeIfAbsent(rid, r -> new MxRoom(this, r));
  }
  
  public MxUser user(String uid) {
    return new MxUser(this, uid);
  }
  
  
  
  public static Obj syncFilter(int count, boolean lazyLoadMembers, boolean unreadThreadNotifications) {
    Obj stateFilter = new Obj();
    if (lazyLoadMembers) stateFilter.put("lazy_load_members", JSON.TRUE);
    if (unreadThreadNotifications) stateFilter.put("unread_thread_notifications", JSON.TRUE);
    Obj room = Obj.fromKV("state", stateFilter);
    if (count!=-1) room.put("timeline", Obj.fromKV("limit", count));
    return Obj.fromKV("room", room);
  }
  public Obj sync(Obj filter) {
    return requestV3("sync").prop("filter", filter.toString()).gToken().get().runJ();
  }
  public String latestBatch() {
    return sync(syncFilter(1, true, false)).str("next_batch");
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
  
  
  public Obj messagesSince(Obj filter, String since, int timeout) {
    return requestV3("sync").prop("since",since).optProp("filter", filter==null? null : filter.toString()).prop("timeout",timeout).gToken().get().runJ();
  }
  
  
  private static boolean hide_data = false;
  public static String redactAccessToken(String uri) {
    return uri.replaceAll("access_token=[^&]+", "access_token=<redacted>");
  }
  private void log(String method, String uri, String data) {
    if (uri.startsWith("/")) System.err.println("!!!!!!!!!!!!! STARTING SLASH !!!!!!!!!!!!!");
    String df = data==null? "" : " "+(data.length()>100 || hide_data? "..." : data);
    Utils.log("mxq", method+" "+url+"/"+redactAccessToken(uri)+df);
  }
  public static void log(String s) {
    Utils.log("mx", s);
  }
  
  public boolean handleError(Obj j, String do_what) {
    if (!j.has("errcode")) return false;
    Utils.warn("Failed to "+do_what+": "+j);
    return true;
  }
  
  
  public static boolean isMxc(String uri) {
    return uri.startsWith("mxc://");
  }
  public Mxc readMxc(String mxc) { // mxc components are guaranteed to be [a-zA-Z0-9_-]
    if (!isMxc(mxc)) return null;
    String[] ps = mxc.substring(6).split("/");
    if (ps.length!=2) return null;
    return new Mxc(ps[0], ps[1]);
  }
  
  public boolean directMediaUrlsNotSupported() {
    return supportedVersions.contains("v1.11");
  }
  public Request mxcDownloadRequest(String mxc) {
    Mxc p = readMxc(mxc);
    if (p==null) return null;
    return directMediaUrlsNotSupported()? requestV(1, "media", "download", p.server, p.media).gToken() : requestRaw("media", "r0", "download", p.server, p.media);
  }
  
  public enum ThumbnailMode { CROP("crop"), SCALE("scale"); final String s; ThumbnailMode(String s) { this.s = s; } }
  public Request mxcThumbnailRequest(String mxc, int w, int h, ThumbnailMode mode) {
    Mxc p = readMxc(mxc);
    if (p==null) return null;
    Request rq = directMediaUrlsNotSupported()? requestV(1, "media", "thumbnail", p.server, p.media).gToken() : requestRaw("media", "v3", "thumbnail", p.server, p.media);
    return rq.prop("width", w).prop("height", h).prop("mode", mode.s);
  }
  
  public static class Mxc {
    public final String server, media;
    public Mxc(String server, String media) {
      this.server = server;
      this.media = media;
    }
    
    public String link() {
      return "mxc://"+server+"/"+media;
    }
  }
}
