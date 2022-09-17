package chat.mx;

import chat.*;
import chat.ui.*;
import chat.ui.Extras.LinkType;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import libMx.*;

import java.util.function.Consumer;

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
    r.openTranscript(target, v -> {}, false);
  }
  
  private int bodyUpdateCtr = 0;
  public void updateBody(boolean live) {
    bodyUpdateCtr++;
    
    if (visible && m0.replyId!=null && !replyRequested) {
      replyRequested = true;
      MxChatEvent tg = log.get(m0.replyId);
      if (tg!=null) {
        String uid = tg.e0.uid;
        String name = tg.username;
        if (name==null || name.isEmpty()) name = r.getUsername(uid);
        bodyPrefix = r.pill(tg.e0, uid, name==null? uid : name) + " ";
      } else {
        Log.fine("mx", "Loading reply info for "+id+"→"+m0.replyId);
        r.u.queueRequest(null, m0::reply, rm -> {
          if (rm==null) {
            bodyPrefix = "[unknown reply] ";
            Log.warn("mx", "Unknown reply ID "+m0.replyId);
            updateBody(false);
          } else {
            bodyPrefix = r.pill(rm.fakeEvent(), rm.uid, r.getUsername(rm.uid)) + " ";
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
        String safeURL = getURL(s<=1);
        
        Consumer<String> toLink = url -> {
          TextNode link = HTMLParser.link(r, url, LinkType.IMG);
          link.add(new StringNode(n.ctx, url));
          r.m.updMessage(this, link, live);
        };
  
        String linkURL = getURL(false);
        if (s>0 && safeURL!=null) {
          TextNode tmpLink = HTMLParser.link(r, linkURL, LinkType.IMG);
          tmpLink.add(n.ctx.makeHere(n.gc.getProp("chat.msg.imageLoadingP").gr()));
          r.m.updMessage(this, tmpLink, live);
  
          String rawURL = getRawURL();
          int expect = bodyUpdateCtr;
          Consumer<Node> got = n -> {
            if (n==null) {
              toLink.accept(linkURL);
            } else if (visible && expect==bodyUpdateCtr) {
              r.m.updMessage(this, n, false);
            }
          };
  
          if (MxServer.isMxc(rawURL) && !JSON.Obj.path(e0.ct, JSON.Str.E, "info", "mimetype").str().equals("image/gif")) { // TODO checking for gif specifically is stupid
            r.u.loadMxcImg(rawURL, got, ImageNode.InlineImageNode::new, r.m.gc.getProp("chat.image.maxW").len(), r.m.gc.getProp("chat.image.maxH").len(), MxServer.ThumbnailMode.SCALE, () -> true);
          } else {
            r.u.loadImg(safeURL, got, ImageNode.InlineImageNode::new, () -> true);
          }
        } else {
          if (linkURL==null) {
            r.m.updMessage(this, new StringNode(n.ctx, "(no URL for image provided)"), live);
          } else {
            toLink.accept(linkURL);
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
  
  
  private String getRawURL() {
    return m0.ct.str("url", null);
  }
  private String getURL(boolean safe) { // returns null if unknown or unsafe
    String body = getRawURL();
    if (body==null) return null;
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
