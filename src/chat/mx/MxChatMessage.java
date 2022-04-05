package chat.mx;

import chat.*;
import chat.ui.MsgNode;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import libMx.*;

import java.nio.charset.StandardCharsets;

public class MxChatMessage extends MxChatEvent {
  public final MxMessage m0;
  
  public MxChatMessage(MxMessage m0, MxEvent e, MxLog log, boolean isNew) {
    super(log, e, m0.edit==1? m0.editsId : m0.id, m0.replyId);
    this.m0 = m0;
    mine = m0.uid.equals(r.u.u.uid);
    username = r.getUsername(m0.uid);
    edited = m0.edit != 0;
    setBody(m0, isNew);
  }
  
  public void edit(MxMessage nm, boolean live) {
    edited = true;
    setBody(nm, live);
  }
  
  private final Counter setBodyCtr = new Counter();
  public void setBody(MxMessage nm, boolean isNew) {
    type = nm.type;
    body = nm.fmt.html;
    src = nm.fmt.body;
    if (m0.replyId != null) {
      MxChatEvent tg = log.get(m0.replyId);
      if (tg!=null) {
        String uid = tg.e.uid;
        String name = tg.username;
        if (name==null || name.isEmpty()) name = r.usernames.get(uid);
        body = r.pill(tg.e, uid, name==null? uid : name) + " " + body;
      } else {
        r.u.queueRequest(setBodyCtr, m0::reply, rm -> {
          if (rm==null) {
            body = "[unknown reply] "+body;
            updateBody(false);
          } else {
            String ruid = rm.uid;
            String name = r.usernames.get(ruid);
            if (name!=null) {
              body = r.pill(rm.fakeEvent(), rm.uid, name) + " " + body;
              updateBody(false);
            } else ChatMain.warn("Unknown user "+ruid+" while getting reply");
          }
        });
      }
    }
    updateBody(isNew);
  }
  
  
  public boolean userEq(ChatEvent o) {
    return o instanceof MxChatMessage && e.uid.equals(((MxChatEvent) o).e.uid);
  }
  
  public void toTarget() {
    MxChatEvent m = log.get(target);
    if (m!=null) m.highlight(false);
    else r.openTranscript(target, v -> {});
  }
  
  private final Counter updateBodyCtr = new Counter();
  public void updateBody(boolean live) {
    switch (type) {
      case "deleted":
        if (!visible) return;
        
        r.m.updMessage(n, this, n.ctx.makeHere(n.gc.getProp("chat.msg.removedP").gr()), live);
        break;
      case "m.image":
        if (!visible) return;
        
        int s = r.m.imageSafety();
        String loadUrl = getURL(s<=1);
        
        if (s>0 && loadUrl!=null) {
          TextNode tmpLink = HTMLParser.link(r, getURL(false));
          tmpLink.add(n.ctx.makeHere(n.gc.getProp("chat.msg.imageLoadingP").gr()));
          r.m.updMessage(n, this, tmpLink, live);
          
          r.u.queueRequest(updateBodyCtr, () -> {
            CacheObj o = CacheObj.forID(loadUrl.getBytes(StandardCharsets.UTF_8));
            byte[] v = o.find();
            if (v==null) {
              MxServer.log("img", "Load image "+loadUrl);
              o.store(v = Tools.get(loadUrl));
            }
            return v;
          }, data -> {
            if (visible) { // room may have changed by the time the image loads
              r.m.updMessage(n, this, HTMLParser.image(r, loadUrl, data), false);
            }
          });
        } else {
          String url = getURL(false);
          TextNode link = HTMLParser.link(r, url);
          link.add(new StringNode(n.ctx, url));
          r.m.updMessage(n, this, link, live);
        }
        break;
      case "m.file":
      case "m.audio":
      case "m.video":
        if (!visible) return;
        
        String url = getURL(false);
        TextNode link = HTMLParser.link(r, url);
        link.add(new StringNode(n.ctx, url));
        r.m.updMessage(n, this, link, live);
        break;
      default:
        Node disp = HTMLParser.parse(r, body);
        if (type.equals("m.emote")) {
          Node n = new TextNode(disp.ctx, Node.KS_NONE, Node.VS_NONE);
          n.add(new StringNode(disp.ctx, "· "+username+" "));
          n.add(disp);
          disp = n;
        } else if (!type.equals("m.text") && !type.equals("m.notice")) ChatMain.warn("Message with type " + type);
        if (live && containsPill(disp)) r.pinged();
        
        if (!visible) return;
        r.m.updMessage(n, this, disp, live);
        break;
    }
  }
  
  
  private String getURL(boolean safe) { // returns null if unknown or unsafe
    String body;
    
    JSON.Obj c = m0.ct;
    if (c.hasStr("url")) body = c.str("url");
    else return null;
    
    if (body.startsWith("mxc://")) return r.u.s.mxcToURL(body);
    if (safe) return null;
    return body;
  }
  
  private static boolean containsPill(Node n) {
    if (n instanceof HTMLParser.Pill) return ((HTMLParser.Pill)n).mine;
    for (Node c : n.ch) if (containsPill(c)) return true;
    return false;
  }
  
  public String getSrc() {
    return src;
  }
  
  public MsgNode.MsgType type() {
    return MsgNode.MsgType.MSG;
  }
  
  public boolean important() {
    return true;
  }
}
