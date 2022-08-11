package chat.mx;

import chat.*;
import chat.ui.*;
import chat.ui.Extras.LinkType;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import libMx.*;

public class MxChatMessage extends MxChatEvent {
  public final MxMessage m0;
  private String bodyPrefix = ""; // from reply
  private boolean replyRequested;
  
  public MxChatMessage(MxMessage m0, MxEvent e0, MxLog log, boolean isNew) {
    super(log, e0, m0.edit==1? m0.editsId : m0.id, m0.replyId);
    this.m0 = m0;
    mine = m0.uid.equals(r.u.u.uid);
    username = r.getUsername(m0.uid);
    edited = m0.edit != 0;
    setBody(m0, isNew);
    if (!isNew) loadReactions();
  }
  
  public void edit(MxEvent ne, boolean live) {
    MxMessage nm = ne.m;
    Log.fine("mx", id+" has been edited to type "+nm.type);
    lastEvent = ne;
    edited = true;
    setBody(nm, live);
  }
  
  public void setBody(MxMessage nm, boolean isNew) {
    type = nm.type;
    body = nm.fmt.html;
    src = nm.fmt.body;
    updateBody(isNew);
  }
  
  
  public boolean userEq(ChatEvent o) {
    return o instanceof MxChatMessage && e0.uid.equals(((MxChatEvent) o).e0.uid);
  }
  
  public void toTarget() {
    MxChatEvent m = log.get(target);
    if (m!=null) m.highlight(false);
    else r.openTranscript(target, v -> {});
  }
  
  private final Counter updateBodyCtr = new Counter();
  public void updateBody(boolean live) {
    if (visible && m0.replyId!=null && !replyRequested) {
      replyRequested = true;
      MxChatEvent tg = log.get(m0.replyId);
      if (tg!=null) {
        String uid = tg.e0.uid;
        String name = tg.username;
        if (name==null || name.isEmpty()) name = r.usernames.get(uid);
        bodyPrefix = r.pill(tg.e0, uid, name==null? uid : name) + " ";
      } else {
        Log.fine("mx", "Loading reply info for "+id+"→"+m0.replyId);
        r.u.queueRequest(null, m0::reply, rm -> {
          if (rm==null) {
            bodyPrefix = "[unknown reply] ";
            Log.warn("mx", "Unknown reply ID "+m0.replyId);
            updateBody(false);
          } else {
            String ruid = rm.uid;
            String name = r.usernames.get(ruid);
            bodyPrefix = r.pill(rm.fakeEvent(), rm.uid, name!=null? name : ruid) + " ";
            updateBody(false);
          }
        });
      }
    }
    
    switch (type) {
      case "deleted":
        if (!visible) return;
        
        r.m.updMessage(this, n.ctx.makeHere(n.gc.getProp("chat.msg.removedP").gr()), live);
        break;
      case "m.image":
        if (!visible) return;
        
        int s = r.m.imageSafety();
        String loadUrl = getURL(s<=1);
        
        if (s>0 && loadUrl!=null) {
          TextNode tmpLink = HTMLParser.link(r, getURL(false), LinkType.IMG);
          tmpLink.add(n.ctx.makeHere(n.gc.getProp("chat.msg.imageLoadingP").gr()));
          r.m.updMessage(this, tmpLink, live);
          
          r.u.queueRequest(updateBodyCtr,
            () -> HTMLParser.inlineImage(r.u, loadUrl, MxChatUser.get("Load image", loadUrl), false), // TODO the ImageNode made by this will take a long time to draw; precompute/cache somehow?
            data -> {
              if (visible) { // room may have changed by the time the image loads
                r.m.updMessage(this, data, false);
              }
            }
          );
        } else {
          String url = getURL(false);
          if (url==null) {
            r.m.updMessage(this, new StringNode(n.ctx, "(no URL for image provided)"), live);
          } else {
            TextNode link = HTMLParser.link(r, url, LinkType.IMG);
            link.add(new StringNode(n.ctx, url));
            r.m.updMessage(this, link, live);
          }
        }
        break;
      case "m.file":
      case "m.audio":
      case "m.video":
        if (!visible) return;
        
        String url = getURL(false);
        if (url==null) {
          r.m.updMessage(this, new StringNode(n.ctx, "(no URL for file provided)"), live);
        } else {
          String mime = m0.ct.obj("info", JSON.Obj.E).str("mimetype", "");
          LinkType t = LinkType.UNK;
          if (type.equals("m.file") && mime.startsWith("text/")) t = LinkType.TEXT;
          
          TextNode link = HTMLParser.link(r, url, t);
          link.add(new StringNode(n.ctx, url));
          r.m.updMessage(this, link, live);
        }
        break;
      default:
        Node disp = HTMLParser.parse(r, bodyPrefix+body);
        if (type.equals("m.emote")) {
          Node n = new TextNode(disp.ctx, Node.KS_NONE, Node.VS_NONE);
          n.add(new StringNode(disp.ctx, "· "+username+" "));
          n.add(disp);
          disp = n;
        } else if (!type.equals("m.text") && !type.equals("m.notice")) Log.warn("mx", "Message with type " + type);
        if (live && containsPill(disp)) r.pinged();
        
        if (!visible) return;
        r.m.updMessage(this, disp, live);
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
