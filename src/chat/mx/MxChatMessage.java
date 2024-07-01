package chat.mx;

import chat.*;
import chat.ui.Extras.LinkType;
import chat.ui.*;
import chat.utils.HTMLParser;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import dzaima.utils.JSON.Obj;
import libMx.*;

import java.util.function.Consumer;

public class MxChatMessage extends MxChatEvent {
  public final MxMessage m0;
  private String bodyPrefix = ""; // from reply
  private boolean replyRequested;
  
  public MxChatMessage(MxMessage m0, MxEvent e0, MxChatroom r, boolean isNew) {
    super(r, m0.uid.equals(r.u.u.uid), e0, m0.id, m0.replyId);
    assert !m0.isEditEvent();
    this.m0 = m0;
    edited = m0.latestFmt!=null;
    setBody(m0, isNew);
    if (!isNew) loadReactions();
  }
  
  public void edit(MxEvent ne, boolean live) {
    MxMessage nm = ne.m;
    Log.fine("mx edit", id+" has been edited to type "+nm.type);
    lastEvent = ne;
    edited = true;
    setBody(nm, live);
  }
  
  public void setBody(MxMessage nm, boolean isNew) {
    type = nm.type;
    MxFmted f = nm.latestFmt!=null? nm.latestFmt : nm.fmt;
    body = f.html;
    src = f.body;
    updateBody(isNew);
  }
  
  public String senderDisplay() {
    return r.getUsername(m0.uid, true).best();
  }
  public boolean userEq(ChatEvent o) {
    return o instanceof MxChatMessage && e0.uid.equals(((MxChatEvent) o).e0.uid);
  }
  
  public void toTarget() {
    r.highlightMessage(target, null, false);
  }
  
  private int bodyUpdateCtr = 0;
  public void updateBody(boolean live) {
    bodyUpdateCtr++;
    
    if ((visible || live) && m0.replyId!=null && !replyRequested) { // `|| live` to get pings
      replyRequested = true;
      MxChatEvent tg = r.editRootEvent(m0.replyId);
      if (tg!=null) {
        String uid = tg.e0.uid;
        if (live && r.u.u.uid.equals(uid)) addPingFromThis();
        String name = tg.senderDisplay();
        if (name==null || name.isEmpty()) name = r.getUsername(uid, true).best();
        bodyPrefix = r.pill(tg.e0, uid, name==null? uid : name) + " ";
      } else {
        Log.fine("mx", "Loading reply info for "+id+"→"+m0.replyId);
        Obj filter = MxRoom.roomEventFilter(true);
        filter.put("types", JSON.Arr.of("m.room.message", "m.room.member"));
        r.u.queueRequest(() -> r.r.msgContext(filter, m0.replyId, 0), ctx -> {
          ok: if (ctx!=null) {
            MxEvent msg = new Vec<>(ctx.events).linearFind(c -> c.id.equals(m0.replyId));
            if (msg==null) break ok;
            
            MxEvent member = new Vec<>(ctx.states).linearFind(c -> c.type.equals("m.room.member") && c.o.str("state_key","").equals(c.uid));
            String displayname = member==null? null : member.ct.str("displayname", null);
            if (displayname==null) displayname = r.getUsername(msg.uid, true).best();
            r.loadQuestionableMemberState(ctx);
            
            if (live && r.u.u.uid.equals(msg.uid)) addPingFromThis();
            bodyPrefix = r.pill(msg, msg.uid, displayname) + " ";
            updateBody(false);
            return;
          }
          bodyPrefix = "[unknown reply] ";
          Log.warn("mx", "Unknown reply ID "+m0.replyId);
          updateBody(false);
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
        Obj info = Obj.path(e0.ct, Obj.E, "info").obj(Obj.E);
        if (s>0 && safeURL!=null) {
          
          String rawURL = getRawURL();
          boolean isMxc = MxServer.isMxc(rawURL);
          ImageNode.InlineImageNode placeholder = new ImageNode.InlineImageNode(n.ctx, info.getInt("w", 0), info.getInt("h", 0), n.ctx.make(n.gc.getProp("chat.msg.imageLoadingP").gr()));
          r.m.updMessage(this, HTMLParser.inlineImagePlaceholder(r.u, isMxc? r.u.s.mxcToURL(rawURL) : rawURL, placeholder), live);
          
          int expect = bodyUpdateCtr;
          Consumer<Node> got = n -> {
            if (!visible) return;
            if (n==null) {
              toLink.accept(linkURL);
            } else if (expect==bodyUpdateCtr) {
              r.m.updMessage(this, n, false);
            }
          };
          
          if (isMxc && !info.str("mimetype", "").equals("image/gif")) { // TODO checking for gif specifically is stupid
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
          String mime = m0.ct.obj("info", Obj.E).str("mimetype", "");
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
          Node n = new TextNode(disp.ctx, Props.none());
          n.add(new StringNode(disp.ctx, "· "+senderDisplay()+" "));
          n.add(disp);
          disp = n;
        } else if (!type.equals("m.text") && !type.equals("m.notice")) Log.warn("mx", "Message with type " + type);
        if (live && containsMyPill(disp)) addPingFromThis();
        
        if (!visible) return;
        r.m.updMessage(this, disp, live);
        break;
    }
  }
  
  private void addPingFromThis() {
    for (MxLog l : r.allLogsOf(e0)) r.addPing(l, this);
    r.unreadChanged();
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
  
  private static boolean containsMyPill(Node n) {
    if (n instanceof HTMLParser.Pill) return ((HTMLParser.Pill)n).mine;
    for (Node c : n.ch) if (containsMyPill(c)) return true;
    return false;
  }
  
  public String getSrc() {
    return src;
  }
  
  public MsgNode.MsgType type() {
    return MsgNode.MsgType.MSG;
  }
  
  public boolean increasesUnread() {
    return true;
  }
}
