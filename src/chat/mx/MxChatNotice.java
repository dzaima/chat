package chat.mx;

import chat.ChatEvent;
import chat.ui.MsgNode;
import chat.utils.HTMLParser;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.JSON.Obj;
import dzaima.utils.Vec;
import libMx.MxEvent;

import java.util.Objects;

public class MxChatNotice extends MxChatEvent {
  public final MxEvent e;
  public final String euid, eName; // executer user ID & name
  
  public MxChatNotice(MxChatroom r, MxEvent e, boolean newAtEnd) {
    super(r, false, e, e.id, null);
    this.e = e;
    this.euid = e.uid;
    eName = r.getUsername(euid, false).best();
    if (!newAtEnd) loadReactions();
  }
  
  public MsgNode.MsgType type() { return MsgNode.MsgType.NOTICE; }
  
  public String senderDisplay() { return ""; }
  public boolean userEq(ChatEvent o) { return false; }
  
  public boolean increasesUnread() {
    return e.type.equals("m.room.encrypted"); // TODO maybe have some setting to set this to always true for rooms you're a mod/admin in?
  }
  
  public String getSrc() { return "?"; }
  public void toTarget() { }
  
  public void updateBody(boolean newAtEnd, boolean ping) {
    if (visible) {
      Node disp = n.ctx.make(n.gc.getProp("chat.msg.noticeP").gr());
      Node ch = disp.ctx.id("ch");
      if ("deleted".equals(type)) {
        r.m.updMessage(this, removedBody(), ping);
        return;
      }
      switch (e.type) {
        case "m.room.member":
          String tuid = e.o.str("state_key", ""); // target user ID
          String tDName = e.ct.str("displayname", null); // target chosen name
          String tName; // some name for target
          tName = tDName==null? r.getUsername(tuid, false).best() : tDName;
          
          Vec<Node> nds = new Vec<>();
          Obj prev = Obj.path(e.o, Obj.E, "unsigned", "prev_content").obj();
          String prevMembership = prev.str("membership", "");
          switch (e.ct.str("membership", "")) {
            case "join":
              if (!prevMembership.equals("join")) {
                nds.add(mk("chat.notice.$join", "user", mkp(tuid, tName)));
              } else {
                String prevName = prev.str("displayname", null);
                Vec<Node> list = new Vec<>();
                
                if (!Objects.equals(prevName, tDName)) list.add(tDName==null? mk("chat.notice.$noName") : mk("chat.notice.$setName", "new", mks(tName)));
                
                if (!Objects.equals(prev.str("avatar_url", null), e.ct.str("avatar_url", null))) list.add(mk("chat.notice.$newAvatar"));
                
                if (list.sz==0) list.add(mk("chat.notice.$noopMember")); // TODO ignore if not developer mode or something, when that exists
                
                nds.add(mkp(tuid, prevName==null? tuid : prevName));
                for (int i = 0; i < list.size(); i++) {
                  if (i>0) nds.add(mk("chat.notice.$and"));
                  nds.add(list.get(i));
                }
              }
              break;
            case "invite": nds.add(mk("chat.notice.$invite", "executer", mkp(euid, eName), "user", mkp(tuid, tName))); break;
            case "leave":
              if (euid.equals(tuid)) {
                nds.add(mk("chat.notice.$left", "user", mkp(tuid, tName)));
              } else {
                nds.add(mk(prevMembership.equals("ban")? "chat.notice.$unban" : prevMembership.equals("invite")? "chat.notice.$uninvite" : "chat.notice.$kick", "executer", mkp(euid, eName), "user", mkp(tuid, tName)));
                if (e.ct.hasStr("reason")) nds.add(mks(": "+e.ct.str("reason")));
              }
              break;
            case "ban":
              nds.add(mk("chat.notice.$ban", "executer", mkp(euid, eName), "user", mkp(tuid, tName)));
              if (e.ct.hasStr("reason")) nds.add(mks(": "+e.ct.str("reason")));
              break;
            case "knock":
              if (!euid.equals(tuid)) {
                nds.add(mk("chat.notice.$requestAccess", "user", mkp(tuid, tName)));
                break;
              }
              /* fallthrough */
            default: nds.add(mk("chat.notice.$defaultMember", "executer", mkp(euid, eName), "user", mkp(tuid, tName), "type", mks(e.ct.str("membership", "m.room.member")))); break;
          }
          for (Node c : nds) ch.add(c);
          break;
        case "m.room.create":             ch.add(mk("chat.notice.$createRoom",     "executer", mkp(euid, eName))); break;
        case "m.room.power_levels":       ch.add(mk("chat.notice.$powerLevels",    "executer", mkp(euid, eName))); break;
        case "m.room.canonical_alias":    ch.add(mk("chat.notice.$canonicalAlias", "executer", mkp(euid, eName))); break;
        case "m.room.join_rules":         ch.add(mk("chat.notice.$joinRules",      "executer", mkp(euid, eName), "rule", mks(e.ct.str("join_rule", "undefined")))); break;
        case "m.room.history_visibility": ch.add(mk("chat.notice.$historyVis",     "executer", mkp(euid, eName), "vis" , mks(e.ct.str("history_visibility", "undefined")))); break;
        case "m.room.name":               ch.add(mk("chat.notice.$roomName",       "executer", mkp(euid, eName), "name", mks(e.ct.str("name", "undefined")))); break;
        case "m.room.guest_access":       ch.add(mk("chat.notice.$guestAccess",    "executer", mkp(euid, eName), "val",  mks(e.ct.str("guest_access", "undefined")))); break;
        default: ch.add(mk("chat.notice.$defaultEvent", "executer", mkp(euid, eName), "type", mks(e.type))); break;
      }
      r.m.updMessage(this, disp, ping);
    }
  }
  
  public Node mk(String prop, Object... kv) {
    return n.ctx.makeKV(n.gc.getProp(prop).gr(), kv);
  }
  public Node mkp(String uid, String name) {
    return HTMLParser.pillLink(r, mks(name), uid);
  }
  public Node mks(String text) {
    return new StringNode(n.ctx, text);
  }
}
