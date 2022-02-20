package libMx;

import dzaima.utils.JSON.Obj;

public class MxFmted {
  public final String body;
  public final String html;
  
  public MxFmted(Obj o) {
    if (!o.has("body")) {
      body = "(deleted)";
      html = "(deleted)";
      return;
    }
    body = o.str("body");
    String h;
    if ("org.matrix.custom.html".equals(o.str("format", ""))) h = o.str("formatted_body");
    else h = Utils.toHTML(body);
    html = h;
  }
  
  public MxFmted(String body, String html) {
    this.body = body;
    this.html = html;
  }
}