package libMx;

import dzaima.utils.JSON;

public abstract class MxSendMsg {
  public abstract String msgJSON();
  
  public static MxSendMsg image(String url, String body, int size, int w, int h) {
    JSON.Obj info = new JSON.Obj();
    if (size!=-1) info.put("size", new JSON.Num(size));
    if (w   !=-1) info.put("w",    new JSON.Num(w));
    if (h   !=-1) info.put("h",    new JSON.Num(h));
    info.put("mimetype", new JSON.Str("image/png"));
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
