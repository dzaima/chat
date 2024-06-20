package libMx;

import dzaima.utils.JSON.*;

public class MxFmt extends MxSendMsg {
  public final StringBuilder body;
  public final StringBuilder html;
  public Type type = Type.TEXT;
  
  public enum Type {
    TEXT("m.text"), EMOTE("m.emote"), NOTICE("m.notice");
    final String msgtype; Type(String msgtype) { this.msgtype = msgtype; }
  }
  
  public MxFmt() {
     body = new StringBuilder();
     html = new StringBuilder();
  }
  public MxFmt(String body, String html) {
    this.body = new StringBuilder(body);
    this.html = new StringBuilder(html);
  }
  
  public void replyTo(MxRoom r, String mid) {
    replyToBase(mid);
    html.insert(0, "<mx-reply><a href="+htmlQuote(r.linkMsg(mid))+"> ↰ </a> </mx-reply>");
  }
  public void reply(MxRoom r, String mid, String uid, String username) {
    assert replyID==null;
    replyID = mid;
    html.insert(0, "<mx-reply><a href="+htmlQuote(r.linkMsg(mid))+"> ↰ </a> "+userHTML(uid, username)+" </mx-reply>");
  }
  
  public void txt(String text) {
    body.append(text);
    html.append(Utils.toHTML(text));
  }
  public void i(String text) {
    body.append("_").append(text).append("_");
    html.append("<em>").append(Utils.toHTML(text)).append("</em>");
  }
  public void b(String text) {
    body.append("**").append(text).append("**");
    html.append("<strong>").append(Utils.toHTML(text)).append("</strong>");
  }
  public void del(String text) {
    body.append("---").append(text).append("---");
    html.append("<del>").append(Utils.toHTML(text)).append("</del>");
  }
  public void raw(String f, String h) {
    body.append(f);
    html.append(h);
  }
  
  public void a(String text, String href) {
    body.append("[").append(text).append("](").append(href).append(")");
    html.append("<a href=").append(htmlQuote(href)).append(">").append(Utils.toHTML(text)).append("</a>");
  }
  private static String htmlQuote(String s) {
    return "\""+Utils.toHTML(s)+"\"";
  }
  public static String userURL(String uid) {
    return "https://matrix.to/#/"+Utils.toHTML(uid);
  }
  public static String userHTML(String uid, String nick) {
    return "<a href="+htmlQuote(userURL(uid))+">"+Utils.toHTML(nick)+"</a>";
  }
  public void user(String uid, String nick) {
    body.append(nick);
    html.append(userHTML(uid, nick));
  }
  
  public void c(String code) {
    body.append("`").append(code.replace("`","\\`")).append("`");
    html.append("<code>").append(Utils.toHTML(code)).append("</code>");
  }
  
  public void mc(String code, String lang) {
    if (body.length()!=0 && body.charAt(body.length()-1)!='\n') body.append("\n");
    body.append("```"); if (lang!=null) body.append(lang);
    body.append("\n").append(code);
    body.append("\n```\n");
    
    html.append("<pre><code");
    if (lang!=null) html.append(" class=\"language-").append(Utils.toHTML(lang)).append("\"");
    html.append(">").append(Utils.toHTML(code, false)).append("</code></pre>");
  }
  
  
  
  
  
  public void f(MxFmt f) {
    body.append(f.body);
    html.append(f.html);
  }
  
  
  public void i(MxFmt f) {
    body.append("_").append(f.body).append("_");
    html.append("<em>").append(f.html).append("</em>");
  }
  public void b(MxFmt f) {
    body.append("**").append(f.body).append("**");
    html.append("<strong>").append(f.html).append("</strong>");
  }
  public void del(MxFmt f) {
    body.append("---").append(f.body).append("---");
    html.append("<del>").append(f.html).append("</del>");
  }
  public void c(MxFmt f) {
    body.append("`").append(f.body).append("`");
    html.append("<code>").append(f.html).append("</code>");
  }
  public void a(MxFmt f, String href) {
    body.append("[").append(f.body).append("](").append(href).append(")");
    html.append("<a href=\"").append(Utils.toHTML(href)).append("\">").append(f.html).append("</a>");
  }
  
  
  public static MxFmt ftxt(String s) {
    MxFmt f = new MxFmt();
    f.txt(s);
    return f;
  }
  
  private void addContent(Obj where, String text, String html) {
    where.put("msgtype", new Str(type.msgtype));
    where.put("body", new Str(text));
    where.put("format", new Str("org.matrix.custom.html"));
    where.put("formatted_body", new Str(html));
  }
  public String msgJSON() {
    Obj ct = new Obj();
    addReplyContent(ct);
    addContent(ct, body.toString(), html.toString());
    return ct.toString();
  }
  public String editJSON(String orig) {
    Obj ctNew = new Obj();
    Obj ct = Obj.fromKV(
      "m.relates_to", Obj.fromKV(
        "rel_type", "m.replace",
        "event_id", orig
      ),
      "m.new_content", ctNew
    );
    String b = body.toString();
    String h = html.toString();
    addContent(ct, "* "+b, "* "+h);
    addContent(ctNew, b, h);
    return ct.toString();
  }
}
