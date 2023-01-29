package chat.mx;

import chat.*;
import chat.ui.*;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;
import dzaima.ui.node.types.editable.EditNode;
import dzaima.utils.*;

import java.util.function.*;

public class ViewProfile {
  final MxChatroom r;
  final ChatMain m;
  final Node base, more;
  final String username, uid;
  final MxChatroom.UserData data;
  boolean banned;
  
  ViewProfile(MxChatroom r, String uid) {
    this.r = r;
    this.m = r.m;
    this.base = m.ctx.make(m.gc.getProp("chat.profile.ui").gr());
    this.more = base.ctx.id("more");
    this.data = r.userData.get(uid);
    this.username = r.getUsername(uid);
    this.uid = uid;
  }
  
  public static void viewProfile(String uid, MxChatroom r) {
    new ViewProfile(r, uid).run();
  }
  
  boolean adminPanel = false;
  void beginAdmin() {
    if (adminPanel) return;
    adminPanel = true;
    make("chat.profile.adminUI");
  }
  Node make(String g) {
    Node n = more.ctx.make(m.gc.getProp(g).gr());
    more.add(n);
    return n;
  }
  
  public void confirm(String path, Consumer<Popup> setup) {
    new Popup(m) {
      protected Rect fullRect() { return centered(m.ctx.vw, 0, 0); }
      protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a) || ChatMain.keyFocus(pw, key, a) || true; }
      protected void unfocused() { close(); }
      protected void setup() { }
      protected void preSetup() {
        node.ctx.id("username").add(new StringNode(m.ctx, username));
        node.ctx.id("room").add(new StringNode(m.ctx, r.title()));
        ((BtnNode) node.ctx.id("cancel")).setFn(b -> close());
        setup.accept(this);
      }
    }.openVW(m.gc, m.ctx, m.gc.getProp(path).gr(), true);
  }
  public void confirmNetwork(String msg, Consumer<String> f, boolean reason, Runnable onDone) {
    confirm("chat.profile.confirmUI", p -> {
      Node n = p.node;
      n.ctx.id("msg").add(new StringNode(n.ctx, msg));
      n.ctx.id("run").add(new StringNode(n.ctx, msg));
      
      Supplier<String> getReason;
      if (reason) {
        Node rf = n.ctx.make(n.gc.getProp("chat.profile.reasonField").gr());
        n.ctx.id("reasonPlace").add(rf);
        getReason = () -> ((EditNode) rf.ctx.id("reason")).getAll();
      } else getReason = () -> null;
      
      ((BtnNode) n.ctx.id("run")).setFn(b -> {
        String got = getReason.get();
        r.u.queueRequest(null, () -> { f.accept(got==null || got.length()==0? null : got); return null; }, v -> onDone.run());
        p.close();
      });
    });
  }
  
  Node banRow;
  public void run() {
    m.rightPanel.make("users", r::viewUsers).add(base);
    base.ctx.id("name").add(new StringNode(m.ctx, username));
    base.ctx.id("server").add(new StringNode(m.ctx, uid));
    
    if (data!=null && data.avatar!=null) {
      Chatroom.URLRes url = r.parseURL(data.avatar);
      if (url.safe) r.user().loadImg(url.url, n -> {
        if (n!=null) base.ctx.id("image").add(n);
      }, ImageNode.ProfilePictureNode::new, () -> true);
    }
    
    ((Extras.ClickableTextNode) base.ctx.id("toReadReceipt")).fn = () -> {
      String id = r.latestReceipts.get(uid);
      if (id!=null) r.openTranscript(id, b -> {}, false);
    };
    ((Extras.ClickableTextNode) base.ctx.id("mention")).fn = () -> {
      r.mentionUser(uid);
    };
    
    int myLevel = r.powerLevels.userLevel(r.u.id());
    
    BiFunction<String, Runnable, Node> link = (s, f) -> {
      beginAdmin();
      Node n = make(s);
      ((Extras.ClickableTextNode) n.ctx.id("run")).fn = f;
      return n;
    };
    
    
    
    if (myLevel >= r.powerLevels.kickReq()) link.apply("chat.profile.kickUI", () -> confirmNetwork("kick", reason -> r.r.kick(uid, reason), true, ()->{}));
    
    if (myLevel >= r.powerLevels.redactReq()) link.apply("chat.profile.removeRecentUI", () -> confirm("chat.profile.removeRecentConfirmUI", p -> {
      Vec<MxChatEvent> es = new Vec<>();
      for (MxChatEvent e : r.log.list) {
        if (!e.e0.uid.equals(uid)) continue; // only the offender's messages
        if (e.isDeleted()) continue; // only non-deleted
        String type = e.e0.type;
        if (type.equals("m.room.member") && e.e0.ct.str("membership", "").equals("join")) continue; // join events are useful to have logged; maybe add option?
        if (type.equals("m.room.create") || type.equals("m.room.server_acl") || type.equals("m.room.encryption")) continue; // copying https://github.com/matrix-org/matrix-react-sdk/blob/32478db57e7cf39be2e1e79f03d4e3ef0f59e925/src/components/views/dialogs/BulkRedactDialog.tsx#L50-L55
        es.add(e);
      }
      p.node.ctx.id("num").replace(0, new StringNode(m.ctx, Integer.toString(es.sz)));
      
      ((BtnNode) p.node.ctx.id("run")).setFn(b -> {
        p.close();
        Runnable[] next = new Runnable[1];
        next[0] = () -> r.u.queueRequest(null, () -> {
          MxChatEvent e = es.pop();
          Log.info("remove recent", "Deleting message "+e.id);
          r.delete(e);
          return es.sz>0;
        }, v -> {
          if (v) next[0].run();
        });
        next[0].run();
      });
    }));
    
    if (myLevel >= r.powerLevels.banReq()) {
      banned = data!=null && data.s==MxChatroom.UserStatus.BANNED;
      
      Runnable banStateUpdated = () -> banRow.ctx.id("text").replace(0, new StringNode(m.ctx, m.gc.getProp(banned? "chat.profile.unbanMsg" : "chat.profile.banMsg").str()));
      banRow = link.apply("chat.profile.banUI", () -> confirmNetwork(banned? "unban" : "ban", reason -> {
        if (banned) r.r.unban(uid);
        else r.r.ban(uid, reason);
      }, !banned, () -> {
        banned^= true;
        banStateUpdated.run();
      }));
      banStateUpdated.run();
      
      // link.apply("chat.profile.advancedBanUI", () -> {
      //  
      // });
    }
  }
}
