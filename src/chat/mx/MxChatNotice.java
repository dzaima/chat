package chat.mx;

import chat.ChatEvent;
import chat.ui.MsgNode;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.JSON.Obj;
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
          String member = e.ct.str("displayname", null);
          if (member==null) member = r.getUsername(e.o.str("state_key", ""));
          String msg;
          switch (e.ct.str("membership", "")) {
            case "join":
              Obj prev = Obj.path(e.o, Obj.E, "unsigned", "prev_content").obj();
              if (!prev.str("membership","").equals("join")) {
                msg = member+" joined";
              } else {
                msg = "";
                String prevName = prev.str("displayname", null);
                if (!Objects.equals(prevName, member)) msg+= (prevName==null? e.uid : prevName)+(member==null? " unset their username" : " changed their display name to "+member);
                String prevAvatar = prev.str("avatar_url", null);
                String currAvatar = e.ct.str("avatar_url", null);
                if (!Objects.equals(prevAvatar, currAvatar)) {
                  if (msg.isEmpty()) msg = prevName+" ";
                  else msg+= " and ";
                  msg+= "changed their avatar";
                }
                if (msg.isEmpty()) msg = executer+" did m.room.member"; // TODO ignore if not developer mode or something, when that exists
              }
              break;
            case "invite": msg = executer+" invited "+member; break;
            case "leave":
              if (executer.equals(member)) msg = member+" left";
              else {
                msg = executer+" kicked "+member;
                if (e.ct.hasStr("reason")) msg+= ": "+e.ct.str("reason");
              }
              break;
            case "ban":
              msg = executer+" banned "+member;
              break;
            case "knock":
              if (!executer.equals(member)) {
                msg = member+" is requesting access to room";
                break;
              }
              /* fallthrough */
            default: msg = executer+" did "+e.ct.str("membership", "m.room.member")+" on "+member; break;
          }
          ch.add(new StringNode(n.ctx, msg));
          break;
        case "m.room.create": ch.add(new StringNode(n.ctx, executer+" created the room")); break;
        case "m.room.power_levels": ch.add(new StringNode(n.ctx, executer+" changed power levels")); break;
        case "m.room.canonical_alias": ch.add(new StringNode(n.ctx, executer+" set canonical alias")); break;
        case "m.room.join_rules": ch.add(new StringNode(n.ctx, executer+" set join_rule to "+e.ct.str("join_rule", "undefined"))); break;
        case "m.room.history_visibility": ch.add(new StringNode(n.ctx, executer+" set history visibility to "+e.ct.str("history_visibility", "undefined"))); break;
        case "m.room.name": ch.add(new StringNode(n.ctx, executer+" set room name to "+e.ct.str("name", "undefined"))); break;
        default: ch.add(new StringNode(n.ctx, executer+" did "+e.type)); break;
      }
      r.m.updMessage(this, disp, live);
    }
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
