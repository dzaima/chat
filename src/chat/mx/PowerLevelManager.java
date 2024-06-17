package chat.mx;

import dzaima.utils.JSON;

import java.util.HashMap;

public class PowerLevelManager {
  public final HashMap<String, Integer> userLevels = new HashMap<>();
  
  public final HashMap<String, Integer> actionReq = new HashMap<>();
  public final HashMap<String, Integer> eventReq = new HashMap<>();
  private static final HashMap<String, Integer> defaultActions = new HashMap<>();
  static {
    defaultActions.put("invite", 0);
    defaultActions.put("kick", 50);
    defaultActions.put("redact", 50);
    defaultActions.put("ban", 50);
    defaultActions.put("events_default", 0);
    defaultActions.put("state_default", 50);
    defaultActions.put("users_default", 0);
  }
  
  public void update(JSON.Obj ct) {
    eventReq.clear();
    for (JSON.Entry e : ct.obj("events", JSON.Obj.E).entries()) eventReq.put(e.k, e.v.asInt());
    
    actionReq.clear();
    defaultActions.forEach((k, d) -> actionReq.put(k, ct.getInt(k, d)));
    
    userLevels.clear();
    for (JSON.Entry e : ct.obj("users", JSON.Obj.E).entries()) userLevels.put(e.k, e.v.asInt());
  }
  
  public int eventReq(String name) {
    Integer g = eventReq.get(name);
    if (g!=null) return g;
    return actionReq.get("events_default");
  }
  public int userLevel(String uid) {
    Integer g = userLevels.get(uid);
    if (g!=null) return g;
    return actionReq.get("users_default");
  }
  
  public int inviteReq() { return actionReq.get("invite"); }
  public int kickReq  () { return actionReq.get("kick"); }
  public int redactReq() { return actionReq.get("redact"); }
  public int banReq   () { return actionReq.get("ban"); }
}
