package chat.mx;

import chat.*;
import chat.mx.PowerLevelManager.Action;
import chat.ui.*;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;
import dzaima.ui.node.types.editable.EditNode;
import dzaima.utils.*;

import java.util.function.*;

public class ViewProfile {
  final MxChatroom viewedRoom;
  final ChatMain m;
  final Node base, more;
  final String me;
  final String uid;
  final Chatroom.Username username;
  final MxChatroom.UserData data;
  boolean banned;
  
  ViewProfile(MxChatroom viewedRoom, String uid) {
    this.viewedRoom = viewedRoom;
    this.m = viewedRoom.m;
    this.base = m.ctx.make(m.gc.getProp("chat.profile.ui").gr());
    this.more = base.ctx.id("more");
    this.data = viewedRoom.userData.get(uid);
    this.username = viewedRoom.getUsername(uid, true);
    this.uid = uid;
    this.me = viewedRoom.u.id();
  }
  
  public static void viewProfile(String uid, Chatroom r) {
    if (r instanceof MxChatroom) new ViewProfile((MxChatroom) r, uid).run();
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
        node.ctx.id("username").add(new StringNode(node.ctx, username.best()));
        Node r = node.ctx.idNullable("room");
        if (r!=null) r.add(new StringNode(node.ctx, ViewProfile.this.viewedRoom.title()));
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
        viewedRoom.u.queueRequest(() -> { f.accept(got==null || got.isEmpty()? null : got); return null; }, v -> onDone.run());
        p.close();
      });
    });
  }
  
  boolean isAutobanned() {
    return viewedRoom.u.autoban.contains(uid);
  }
  
  Node banRow, autobanRow;
  public void run() {
    m.rightPanel.make("users", viewedRoom::viewUsers).add(base);
    username.doubleName((name, lazy) -> base.ctx.id("name").replace(0, new StringNode(m.ctx, name)));
    
    base.ctx.id("server").add(new StringNode(m.ctx, uid));
    
    if (data!=null && data.avatar!=null) {
      ChatUser.URIInfo url = viewedRoom.u.parseURI(data.avatar, null);
      if (url.safe) viewedRoom.user().loadImg(url, true, n -> {
        if (n!=null) base.ctx.id("image").add(n);
      }, ImageNode.ProfilePictureNode::new, () -> true);
    }
    
    ((Extras.ClickableTextNode) base.ctx.id("toReadReceipt")).fn = () -> {
      MxLog log = viewedRoom.visibleLog();
      if (log==null) log = viewedRoom.globalLog();
      String id = log.latestReceipts.get(uid);
      if (id!=null) viewedRoom.highlightMessage(id, null, false);
      else Log.warn("mx", "Unknown read receipt for "+uid);
    };
    ((Extras.ClickableTextNode) base.ctx.id("mention")).fn = () -> {
      LiveView view = viewedRoom.m.view.baseLiveView();
      if (view != null) view.mentionUser(uid);
    };
    
    BiFunction<String, Runnable, Node> link = (s, f) -> {
      beginAdmin();
      Node n = make(s);
      ((Extras.ClickableTextNode) n.ctx.id("run")).fn = f;
      return n;
    };
    
    if (viewedRoom.powerLevels.can(me, Action.KICK)) link.apply("chat.profile.kickUI", () -> confirmNetwork("kick", reason -> viewedRoom.r.kick(uid, reason), true, ()->{}));
    
    if (viewedRoom.powerLevels.can(me, Action.REDACT)) link.apply("chat.profile.removeRecentUI", () -> confirm("chat.profile.removeRecentConfirmUI", p -> {
      Vec<MxChatEvent> es = getDeletableMessages(viewedRoom);
      p.node.ctx.id("num").replace(0, new StringNode(p.node.ctx, Integer.toString(es.sz)));
      
      ((BtnNode) p.node.ctx.id("run")).setFn(b -> {
        p.close();
        doDeleteMessages(viewedRoom, es);
      });
    }));
    
    if (viewedRoom.powerLevels.can(me, Action.BAN)) {
      banned = data!=null && data.s==MxChatroom.UserStatus.BANNED;
      
      Runnable banStateUpdated = () -> banRow.ctx.id("text").replace(0,
        new StringNode(m.ctx, m.gc.getProp(banned? "chat.profile.unbanMsg" : "chat.profile.banMsg").str())
      );
      banRow = link.apply("chat.profile.banUI", () -> confirmNetwork(banned? "unban" : "ban", reason -> {
        if (banned) doUnban(viewedRoom);
        else doBan(viewedRoom, reason);
      }, !banned, () -> {
        banned^= true;
        banStateUpdated.run();
      }));
      banStateUpdated.run();
      
      Runnable autobanStateUpdated = () -> autobanRow.ctx.id("text").replace(0,
        new StringNode(m.ctx, m.gc.getProp(isAutobanned()? "chat.profile.unautobanOption" : "chat.profile.autobanOption").str())
      );
      autobanRow = link.apply("chat.profile.banUI", () -> confirm(isAutobanned()? "chat.profile.unautobanConfirmUI" : "chat.profile.autobanConfirmUI", p -> {
        boolean unban = isAutobanned();
        Box<Vec<MxChatroom>> banNow = new Box<>(null);
        Box<Vec<Pair<MxChatroom, Vec<MxChatEvent>>>> delNow = new Box<>(null);
        if (!unban) {
          Runnable refresh = () -> {
            String s = "";
            if (banNow.get()==null && delNow.get()==null) s = "do nothing";
            if (banNow.get()!=null) s+= "ban the user in "+banNow.get().sz+" rooms";
            if (delNow.get()!=null) {
              if (!s.isEmpty()) s+= "; ";
              int sum = 0;
              for (Pair<MxChatroom, Vec<MxChatEvent>> v : delNow.get()) sum+= v.b.sz;
              s+= "delete "+sum+" messages from across "+delNow.get().sz+" rooms";
            }
            p.node.ctx.id("currAction").replace(0, new StringNode(p.node.ctx, s));
          };
          refresh.run();
          
          ((CheckboxNode) p.node.ctx.id("banNow")).setFn(b -> {
            banNow.set(b? getRoomsWithPerm(Action.BAN).filter(c -> c.lazyHasMember(uid)) : null);
            refresh.run();
          });
          ((CheckboxNode) p.node.ctx.id("delNow")).setFn(b -> {
            delNow.set(b? getRoomsWithPerm(Action.REDACT).map(c -> new Pair<>(c, getDeletableMessages(c))).filter(c -> c.b.sz>0) : null);
            refresh.run();
          });
        }
        
        p.node.ctx.id("userID").add(new StringNode(p.node.ctx, uid));
        BtnNode run = (BtnNode) p.node.ctx.id("run");
        run.add(new StringNode(run.ctx, unban? "unautoban" : "autoban"));
        run.setFn(b -> {
          p.close();
          if (unban) {
            viewedRoom.u.autoban.remove(uid);
          } else {
            viewedRoom.u.autoban.add(uid);
            if (banNow.get()!=null) {
              for (MxChatroom r : banNow.get()) doBan(r, null);
            }
            if (delNow.get()!=null) {
              for (Pair<MxChatroom, Vec<MxChatEvent>> evs : delNow.get()) {
                doDeleteMessages(evs.a, evs.b);
              }
            }
          }
          viewedRoom.u.autobanUpdated();
          viewedRoom.m.requestSave();
          autobanStateUpdated.run();
        });
      }));
      autobanStateUpdated.run();
    }
  }
  
  private void doBan(MxChatroom r, String reason) {
    if (me.equals(uid)) throw new RuntimeException("don't ban yourself!");
    Log.info("mod", "Banning user "+uid+" from "+r.prettyID());
    if (r.m.doRunModtools) r.u.queueNetwork(() -> r.r.ban(uid, reason));
  }
  
  private void doUnban(MxChatroom r) {
    Log.info("mod", "Unbanning user "+uid+" from "+r.prettyID());
    if (r.m.doRunModtools) r.u.queueNetwork(() -> r.r.unban(uid));
  }
  
  private Vec<MxChatroom> getRoomsWithPerm(Action action) {
    return viewedRoom.u.rooms().filter(c -> c.powerLevels.can(me, action));
  }
  
  private Vec<MxChatEvent> getDeletableMessages(MxChatroom room) {
    Vec<MxChatEvent> es = new Vec<>();
    for (MxChatEvent e : room.allKnownEvents.values()) {
      if (!e.e0.uid.equals(uid)) continue; // only the offender's messages
      if (e.isDeleted()) continue; // only non-deleted
      String type = e.e0.type;
      if (type.equals("m.room.member") && e.e0.ct.str("membership", "").equals("join")) continue; // join events are useful to have logged; maybe add option?
      if (type.equals("m.room.create") || type.equals("m.room.server_acl") || type.equals("m.room.encryption")) continue; // copying https://github.com/matrix-org/matrix-react-sdk/blob/32478db57e7cf39be2e1e79f03d4e3ef0f59e925/src/components/views/dialogs/BulkRedactDialog.tsx#L50-L55
      es.add(e);
    }
    return es;
  }
  
  private void doDeleteMessages(MxChatroom r, Vec<MxChatEvent> es) {
    if (me.equals(uid)) throw new RuntimeException("don't delete your own messages!");
    Runnable[] next = new Runnable[1];
    next[0] = () -> r.u.queueRequest(() -> {
      MxChatEvent e = es.pop();
      Log.info("mod", "Deleting message "+e.id);
      assert e.senderID().equals(uid);
      if (r.m.doRunModtools) r.delete(e);
      return es.sz>0;
    }, v -> {
      if (v) next[0].run();
    });
    next[0].run();
  }
}
