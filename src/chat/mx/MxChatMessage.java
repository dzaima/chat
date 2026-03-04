package chat.mx;

import chat.*;
import chat.ui.*;
import chat.ui.Extras.LinkType;
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
  
  public MxChatMessage(MxMessage m0, MxEvent e0, MxChatroom r, boolean newAtEnd, boolean ping) {
    super(r, m0.uid.equals(r.u.u.uid), e0, m0.id, m0.replyId);
    assert !m0.isEditEvent();
    this.m0 = m0;
    edited = m0.latestFmt!=null;
    setBody(m0, newAtEnd, ping);
    if (!newAtEnd) loadReactions();
  }
  
  public void edit(MxEvent ne, boolean ping) {
    MxMessage nm = ne.m;
    Log.fine("mx edit", id+" has been edited to type "+nm.type);
    lastEvent = ne;
    edited = true;
    setBody(nm, false, ping);
  }
  
  public void setBody(MxMessage nm, boolean newAtEnd, boolean ping) {
    type = nm.type;
    MxFmted f = nm.latestFmt!=null? nm.latestFmt : nm.fmt;
    body = f.html;
    src = f.body;
    updateBody(newAtEnd, ping);
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
  public void updateBody(boolean newAtEnd, boolean ping) {
    bodyUpdateCtr++;
    
    if ((visible || ping) && m0.replyId!=null && !replyRequested) {
      replyRequested = true;
      MxChatEvent tg = r.editRootEvent(m0.replyId);
      if (tg!=null) {
        String uid = tg.e0.uid;
        if (ping && r.u.u.uid.equals(uid)) addPingFromThis();
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
            
            bodyPrefix = r.pill(msg, msg.uid, displayname) + " ";
            if (ping && r.u.u.uid.equals(msg.uid)) addPingFromThis();
            updateBody(newAtEnd, false);
            return;
          }
          bodyPrefix = "[unknown reply] ";
          Log.warn("mx", "Unknown reply ID "+m0.replyId);
          updateBody(newAtEnd, false);
        });
      }
    }
    
    switch (type) {
      case "deleted":
        if (!visible) return;
        
        r.m.updMessage(this, removedBody(), newAtEnd);
        break;
      case "m.image":
      case "m.file":
      case "m.audio":
      case "m.video":
        if (!visible) return;
        
        Obj info = new Obj();
        for (JSON.Entry e : Obj.path(e0.ct, Obj.E, "info").obj(Obj.E).entries()) info.put(e.k, e.v);
        String expectedFilename = e0.ct.str("filename", e0.ct.str("body", ""));
        info.put(Extras.EXPECTED_FILENAME, new JSON.Str(expectedFilename));
        
        ChatUser.URIInfo uri = r.u.parseURI(getRawURI(), info);
        
        int s = r.m.imageSafety();
        
        Runnable replaceWithPlainLink = () -> {
          LinkType lt;
          if (type.equals("m.image")) lt = LinkType.IMG;
          else if (type.equals("m.file") && info.str("mimetype", "").startsWith("text/")) lt = LinkType.TEXT;
          else lt = LinkType.UNK;
          
          TextNode link = Extras.textLink(r.u, uri.uri, new Extras.LinkInfo(lt, null, info));
          link.add(new StringNode(n.ctx, expectedFilename));
          r.m.updMessage(this, link, newAtEnd);
        };
        
        if (type.equals("m.image") && s > (uri.safe? 0 : 1)) {
          ImageNode.InlineImageNode placeholder = new ImageNode.InlineImageNode(n.ctx, info.getInt("w", 0), info.getInt("h", 0), n.ctx.make(n.gc.getProp("chat.msg.imageLoadingP").gr()));
          Node nd = HTMLParser.inlineImagePlaceholder(r.u, uri.uri, new Extras.LinkInfo(LinkType.IMG, null, info), placeholder);
          r.m.updMessage(this, nd, newAtEnd);
          
          int expect = bodyUpdateCtr;
          Consumer<Node> got = n -> {
            if (!visible) return;
            if (n==null) {
              replaceWithPlainLink.run();
            } else if (expect==bodyUpdateCtr) {
              r.m.updMessage(this, n, false);
            }
          };
          r.u.loadImg(uri, true, got, ImageNode.InlineImageNode::new, () -> true);
        } else {
          replaceWithPlainLink.run();
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
        if (ping && containsMyPill(disp)) addPingFromThis();
        
        if (!visible) return;
        r.m.updMessage(this, disp, newAtEnd);
        break;
    }
  }
  
  private void addPingFromThis() {
    for (MxLog l : r.allLogsOf(e0)) r.addPing(l, this);
    r.unreadChanged();
  }
  
  private String getRawURI() {
    return m0.ct.str("url", "");
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
