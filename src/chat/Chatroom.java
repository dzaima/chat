package chat;

import chat.ui.*;
import chat.utils.UnreadInfo;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.*;

public abstract class Chatroom {
  public final ChatMain m;
  public final RoomListNode.RoomNode node;
  public String officialName;
  public String typing = "";
  
  public final MuteState muteState;
  public String customName;
  public final RoomEditing editor;
  
  protected Chatroom(ChatUser u) {
    this.m = u.m;
    muteState = new MuteState(m) {
      protected int ownedUnreads() { return unreadInfo().unread; }
      protected boolean ownedPings() { return unreadInfo().ping; }
      protected void updated() { unreadChanged(); muteStateChanged(); }
    };
    
    node = new RoomListNode.RoomNode(this, u);
    setOfficialName("Unnamed room");
    editor = new RoomEditing(u, "chat.rooms.rename.roomField") {
      protected String getName() { return title(); }
      protected Node entryPlace() { return node.ctx.id("entryPlace"); }
      protected void rename(String newName) { setCustomName(newName); }
    };
  }
  
  public abstract LiveView mainView();
  public abstract void muteStateChanged();
  public abstract void cfgUpdated();
  
  public void tick() { muteState.tick(); }
  public abstract String getUsername(String uid, boolean nullIfUnknown);
  
  
  
  public static class UserRes {
    public final String disp, src;
    public UserRes(String disp, String src) { this.disp = disp; this.src = src; }
  }
  public abstract void retryOnFullUserList(Runnable then); // if full user list not already loaded, initiate full user list loading and then.run() afterward
  public abstract Vec<UserRes> autocompleteUsers(String prefix);
  
  public void roomMenu(Click c, int x, int y, Runnable onClose) {
    PartialMenu pm = new PartialMenu(m.gc);
    addMenuItems(pm);
    pm.open(node.ctx, c, onClose);
  }
  public /*open*/ void addMenuItems(PartialMenu pm) {
    muteState.addMenuOptions(pm);
    pm.add(pm.gc.getProp("chat.roomMenu.renameLocally").gr(), "localRename", editor::startEdit);
    pm.add(pm.gc.getProp("chat.roomMenu.makeFolder").gr(), "wrap", () -> RoomListNode.DirStartNode.wrap(user(), node));
  }
  public abstract void userMenu(Click c, int x, int y, String uid);
  public abstract void viewProfile(String uid);
  
  public abstract RoomListNode.ExternalDirInfo asDir();
  
  
  
  public void setOfficialName(String name) {
    this.officialName = name;
    titleUpdated();
  }
  public void setCustomName(String name) {
    this.customName = name;
    titleUpdated();
  }
  public void titleUpdated() {
    node.ctx.id("name").replace(0, new StringNode(node.ctx, title()));
    if (m.view!=null && m.view.room()==this) m.updateCurrentViewTitle();
  }
  
  public String title() { return customName!=null? customName : officialName; }
  public abstract void viewRoomInfo();
  
  public abstract ChatUser user();
  public Chatroom room() { return this; }
  
  public abstract Pair<Boolean, Integer> highlight(String s); // a: whether highlight as markdown; b: command prefix length or 0
  public abstract void delete(ChatEvent m);
  public abstract ChatEvent find(String id);
  public abstract String asCodeblock(String s);
  public static class URLRes {
    public final String url;
    public final boolean safe;
    public URLRes(String url, boolean safe) { this.url = url; this.safe = safe; }
  }
  public abstract URLRes parseURL(String src);
  
  
  
  public abstract UnreadInfo unreadInfo(); // count, ping
  
  public void unreadChanged() {
    RoomListNode.setUnread(m, node, muteState, unreadInfo());
    m.unreadChanged();
    user().updateFolderUnread();
  }
}
