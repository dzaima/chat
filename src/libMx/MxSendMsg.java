package libMx;

import dzaima.utils.JSON;

import java.util.HashMap;

public abstract class MxSendMsg {
  public abstract String msgJSON();
  
  public static MxSendMsg image(String url, String body, String mime, int size, int w, int h) {
    JSON.Obj info = new JSON.Obj(new HashMap<>());
    if (size!=-1) info.put("size", new JSON.Num(size));
    if (w   !=-1) info.put("w",    new JSON.Num(w));
    if (h   !=-1) info.put("h",    new JSON.Num(h));
    info.put("mimetype", new JSON.Str(mime));
    return new MxJSONMsg("{" +
      "\"msgtype\":\"m.image\",\"body\":"+Utils.toJSON(body)+",\"url\":"+Utils.toJSON(url)+","+
      "\"info\":"+info+
    "}");
  }
  
  private static class MxJSONMsg extends MxSendMsg {
    final String json;
    private MxJSONMsg(String json) { this.json = json; }
    public String msgJSON() { return json; }
  }
}
