package chat.mx;

import chat.*;
import chat.ui.MsgNode;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.JSON.Obj;
import dzaima.utils.Vec;
import libMx.MxEvent;

import java.util.Objects;

public class MxChatNotice extends MxChatEvent {
  public final MxEvent e;
  public final String executer;
  
  public MxChatNotice(MxLog log, MxEvent e, boolean live) {
    super(log, e, e.id, null);
    this.e = e;
    executer = r.getUsername(e.uid);
    username = "";
    if (!live) loadReactions();
  }
  
  public boolean userEq(ChatEvent o) { return false; }
  
  public void toTarget() { }
  
  public void updateBody(boolean live) {
    if (visible) {
      Node disp = n.ctx.make(n.gc.getProp("chat.msg.noticeP").gr());
      Node ch = disp.ctx.id("ch");
      if ("deleted".equals(type)) {
        r.m.updMessage(this, n.ctx.makeHere(n.gc.getProp("chat.msg.removedP").gr()), live);
        return;
      }
      switch (e.type) {
        case "m.room.member":
          String currName = e.ct.str("displayname", null);
          String member = e.o.str("state_key", "");
          if (currName==null) currName = r.getUsername(member);
          Vec<Node> nds = new Vec<>();
          Obj prev = Obj.path(e.o, Obj.E, "unsigned", "prev_content").obj();
          String prevMembership = prev.str("membership", "");
          switch (e.ct.str("membership", "")) {
            case "join":
              if (!prevMembership.equals("join")) {
                nds.add(mk("chat.notice.$join", "user", mkp(member, currName)));
              } else {
                String prevName = prev.str("displayname", null);
                Vec<Node> list = new Vec<>();
                if (!Objects.equals(prevName, currName)) {
                  list.add(currName==null? mk("chat.notice.$noName") : mk("chat.notice.$setName", "new", mks(currName)));
                }
                String prevAvatar = prev.str("avatar_url", null);
                String currAvatar = e.ct.str("avatar_url", null);
                if (!Objects.equals(prevAvatar, currAvatar)) {
                  list.add(mk("chat.notice.$newAvatar"));
                }
                if (list.sz==0) list.add(mk("chat.notice.$noopMember")); // TODO ignore if not developer mode or something, when that exists
                
                nds.add(mkp(member, prevName));
                for (int i = 0; i < list.size(); i++) {
                  if (i>0) nds.add(mk("chat.notice.$and"));
                  nds.add(list.get(i));
                }
              }
              break;
            case "invite": nds.add(mk("chat.notice.$invite", "executer", mkp(e.uid, executer), "user", mkp(member, currName))); break;
            case "leave":
              if (executer.equals(currName)) nds.add(mk("chat.notice.$left", "user", mkp(member, currName)));
              else {
                nds.add(mk(prevMembership.equals("ban")? "chat.notice.$unban" : "chat.notice.$kick", "executer", mkp(e.uid, executer), "user", mkp(member, currName)));
                if (e.ct.hasStr("reason")) nds.add(mks(": "+e.ct.str("reason")));
              }
              break;
            case "ban":
              nds.add(mk("chat.notice.$ban", "executer", mkp(e.uid, executer), "user", mkp(member, currName)));
              if (e.ct.hasStr("reason")) nds.add(mks(": "+e.ct.str("reason")));
              break;
            case "knock":
              if (!executer.equals(currName)) {
                nds.add(mk("chat.notice.$requestAccess", "user", mkp(member, currName)));
                break;
              }
              /* fallthrough */
            default: nds.add(mk("chat.notice.$defaultMember", "executer", mkp(e.uid, executer), "user", mkp(member, currName), "type", mks(e.ct.str("membership", "m.room.member")))); break;
          }
          for (Node c : nds) ch.add(c);
          break;
        case "m.room.create":             ch.add(mk("chat.notice.$createRoom",     "executer", mkp(e.uid, executer))); break;
        case "m.room.power_levels":       ch.add(mk("chat.notice.$powerLevels",    "executer", mkp(e.uid, executer))); break;
        case "m.room.canonical_alias":    ch.add(mk("chat.notice.$canonicalAlias", "executer", mkp(e.uid, executer))); break;
        case "m.room.join_rules":         ch.add(mk("chat.notice.$joinRules",      "executer", mkp(e.uid, executer), "rule", mks(e.ct.str("join_rule", "undefined")))); break;
        case "m.room.history_visibility": ch.add(mk("chat.notice.$historyVis",     "executer", mkp(e.uid, executer), "vis" , mks(e.ct.str("history_visibility", "undefined")))); break;
        case "m.room.name":               ch.add(mk("chat.notice.$roomName",       "executer", mkp(e.uid, executer), "name", mks(e.ct.str("name", "undefined")))); break;
        case "m.room.guest_access":       ch.add(mk("chat.notice.$guestAccess",    "executer", mkp(e.uid, executer), "val",  mks(e.ct.str("guest_access", "undefined")))); break;
        default: ch.add(mk("chat.notice.$defaultEvent", "executer", mkp(e.uid, executer), "type", mks(e.type))); break;
      }
      r.m.updMessage(this, disp, live);
    }
  }
  
  public Node mk(String prop, Object... kv) {
    return n.ctx.makeKV(n.gc.getProp(prop).gr(), kv);
  }
  public Node mkp(String uid) {
    return mkp(uid, r.getUsername(uid));
  }
  public Node mkp(String uid, String name) {
    return HTMLParser.pillLink(r, mks(name), uid);
  }
  public Node mks(String text) {
    return new StringNode(n.ctx, text);
  }
  
  public String getSrc() { return "?"; }
  
  public MsgNode.MsgType type() {
    return MsgNode.MsgType.NOTICE;
  }
  
  public boolean ignore() {
    return e.type.equals("m.room.redaction");
  }
  
  public boolean important() {
    return false; // maybe have some setting to set this to true for rooms you're a mod/admin in?
  }
}
