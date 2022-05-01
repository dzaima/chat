package libMx;

import dzaima.utils.JSON;
import dzaima.utils.JSON.Obj;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;


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
      Obj j = postJ("_matrix/client/r0/login",
        "{" +
          "\"user\":"+Utils.toJSON(uid)+"," +
          "\"password\":"+Utils.toJSON(passwd)+"," +
          "\"type\":\"m.login.password\"" +
          "}");
      if (j.has("errcode")) {
        warn("failed to log in");
        return null;
      }
      return new MxLogin(this, uid, j.str("access_token"));
    } finally {
      hide_data = false;
    }
  }
  
  public static Obj parseObj(String s) {
    try {
      return JSON.parseObj(s);
    } catch (Throwable e) {
      System.err.println("Failed parsing JSON: ```");
      System.err.println(s);
      System.err.println("```");
      e.printStackTrace();
      return null;
    }
  }
  public Obj getJ(String path) {
    int failTime = 1;
    while (true) {
      int retryTime = failTime;
      try {
        Obj r = parseObj(getRaw(path)); // TODO catch parse error and try to parse out an HTML error code and throw a custom exception on all parseObj
        if (r!=null && !"M_LIMIT_EXCEEDED".equals(r.str("errcode", null))) return r;
        if (r!=null && r.hasNum("retry_after_ms")) retryTime = Math.max(failTime, r.getInt("retry_after_ms")/1000 + 2);
      } catch (RuntimeException e) { e.printStackTrace(); }
      log("mxq", "Retrying in "+retryTime+"s");
      Utils.sleep(retryTime*1000);
      failTime = Math.min(Math.max(failTime*2, 1), 180);
    }
  }
  public Obj postJ(String path, String data) {
    int failTime = 1;
    while (true) {
      int retryTime = failTime;
      try {
        Obj r = parseObj(postRaw(path, data));
        if (r!=null && !"M_LIMIT_EXCEEDED".equals(r.str("errcode", null))) return r;
        if (r!=null && r.hasNum("retry_after_ms")) retryTime = Math.max(failTime, r.getInt("retry_after_ms")/1000 + 2);
      } catch (RuntimeException e) { e.printStackTrace(); }
      log("mxq", "Retrying in "+retryTime+"s");
      Utils.sleep(retryTime*1000);
      failTime = Math.min(Math.max(failTime*2, 1), 180);
    }
  }
  public Obj putJ(String path, String data) {
    int failTime = 1;
    while (true) {
      int retryTime = failTime;
      try {
        Obj r = parseObj(putRaw(path, data));
        if (r!=null && !"M_LIMIT_EXCEEDED".equals(r.str("errcode", null))) return r;
        if (r!=null && r.hasNum("retry_after_ms")) retryTime = Math.max(failTime, r.getInt("retry_after_ms")/1000 + 2);
      } catch (RuntimeException e) { e.printStackTrace(); }
      log("mxq", "Retrying in "+retryTime+"s");
      Utils.sleep(retryTime*1000);
      failTime = Math.min(Math.max(failTime*2, 1), 180);
    }
  }
  public String postRaw(String path, String data) {
    log("POST", path, data);
    return Utils.post(url+"/"+path, data.getBytes(StandardCharsets.UTF_8));
  }
  public String getRaw(String path) {
    log("GET", path, null);
    return Utils.get(url+"/"+path);
  }
  public String putRaw(String path, String data) {
    log("PUT", path, data);
    return Utils.put(url+"/"+path, data.getBytes(StandardCharsets.UTF_8));
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
    return getJ("_matrix/client/r0/sync?filter={\"room\":{\"timeline\":{\"limit\":"+count+"}}}&access_token="+gToken);
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
      Obj j = postJ("_matrix/client/r0/register?kind=user",
        "{" +
          "\"username\":" +Utils.toJSON(cid   )+"," +
          "\"password\":" +Utils.toJSON(passwd)+"," +
          "\"device_id\":"+Utils.toJSON(device)+"," +
          "\"auth\": {\"type\":\"m.login.dummy\"}" +
        "}");
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
    return getJ("_matrix/client/r0/sync?since="+since+"&timeout="+timeout+"&access_token="+gToken);
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
    warn("Failed to "+do_what+": "+j.toString());
    return true;
  }
  
  public static void log(String id, String s) {
    if (LOG) System.out.println("["+LocalDateTime.now()+" "+id+"] "+s);
  }
  public static void warn(String s) {
    System.err.println("["+LocalDateTime.now()+" !!] "+s);
  }
  public static boolean LOG = true;
  
  public String mxcToURL(String mxc) {
    if (!mxc.startsWith("mxc://")) throw new RuntimeException("not an mxc URL: "+mxc);
    return url+"/_matrix/media/r0/download/"+mxc.substring(6);
  }
}
