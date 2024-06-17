package chat;

import chat.ui.*;
import dzaima.ui.gui.PartialMenu;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.StringNode;
import dzaima.utils.*;

public abstract class Chatroom extends View {
  public final ChatMain m;
  public RoomListNode.RoomNode node;
  public String officialName;
  public String typing = "";
  
  public final MuteState muteState;
  public String customName;
  public final RoomEditing editor;
  public int unread;
  public boolean ping;
  
  public final ChatTextArea input;
  protected Chatroom(ChatUser u) {
    this.m = u.m;
    muteState = new MuteState(m) {
      protected int ownedUnreads() { return unread; }
      protected boolean ownedPings() { return ping; }
      protected void updated() { unreadChanged(); muteStateChanged(); }
    };
    
    node = new RoomListNode.RoomNode(this, u);
    setOfficialName("Unnamed room");
    input = new ChatTextArea(this, Props.keys("family", "numbering").values(new StrProp("Arial"), EnumProp.FALSE));
    input.wrap = true;
    editor = new RoomEditing(u, "chat.rooms.rename.roomField") {
      protected String getName() { return title(); }
      protected Node entryPlace() { return node.ctx.id("entryPlace"); }
      protected void rename(String newName) { setCustomName(newName); }
    };
    cfgUpdated();
  }
  
  public abstract Node inputPlaceContent();
  public abstract void muteStateChanged();
  public void cfgUpdated() {
    if (m.gc.getProp("chat.preview.enabled").b()) input.setLang(MDLang.makeLanguage(m, input));
    else input.setLang(m.gc.langs().defLang);
  }
  
  public long atEndStart;
  public long firstUnreadTime;
  public void tick() {
    muteState.tick();
    long nowMs = m.gc.lastMs;
    if (open && m.focused && m.atEnd() && !muteState.muted) {
      long viewedMs = nowMs - atEndStart;
      if (viewedMs > m.readMinViewMs  ||  viewedMs > (nowMs-firstUnreadTime)*m.altViewMult) markAsRead();
    } else {
      atEndStart = nowMs;
    }
  }
  public void markAsRead() {
    if (unread==0 && !ping) return;
    if (unread!=0) readAll();
    unread = 0;
    ping = false;
    m.updateUnread();
  }
  
  
  public abstract String getUsername(String uid);
  
  public abstract void upload();
  public abstract void mentionUser(String uid);
  
  public static class URLRes {
    public final String url;
    public final boolean safe;
    public URLRes(String url, boolean safe) { this.url = url; this.safe = safe; }
  }
  public abstract URLRes parseURL(String src);
  
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
  }
  public abstract void userMenu(Click c, int x, int y, String uid);
  public abstract void viewProfile(String uid);
  
  public abstract RoomListNode.ExternalDirInfo asDir();
  
  public void viewTick() {
    if (m.toLast!=0) {
      if (m.toLast==3) m.toHighlight.highlight(true); 
      else m.msgsScroll.toYE(m.toLast==2);
      m.toLast = 0;
    } else {
      if (m.msgsScroll.atYS(m.endDist)) older();
    }
  }
  
  
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
    if (open) m.setCurrentRoomTitle(title());
  }
  
  public String title() { return customName!=null? customName : officialName; }
  public abstract void viewRoomInfo();
  
  public abstract ChatUser user();
  public Chatroom room() { return this; }
  
  public boolean open;
  public /*open*/ void show() { open=true; node.updBG(); unreadChanged(); m.setCurrentRoomTitle(title()); input.roomShown(); }
  public /*open*/ void hide() { open=false;node.updBG(); input.roomHidden(); }
  
  public abstract void readAll();
  public abstract void older();
  public abstract Pair<Boolean, Integer> highlight(String s); // a: whether highlight as markdown; b: command prefix length or 0
  public abstract void post(String s, String target);
  public abstract void edit(ChatEvent m, String s);
  public abstract void delete(ChatEvent m);
  public abstract ChatEvent find(String id);
  
  
  
  public abstract void pinged();
  
  
  protected void changeUnread(int addUnread, boolean addPing) {
    if (addUnread==0 && !addPing) return;
    if (unread==0 && !ping) firstUnreadTime = m.gc.lastMs;
    unread+= addUnread;
    ping|= addPing;
  }
  
  public void unreadChanged() {
    RoomListNode.setUnread(m, node, muteState, ping, unread);
    m.unreadChanged();
    user().updateFolderUnread();
  }
  
  public abstract ChatEvent prevMsg(ChatEvent msg, boolean mine);
  public abstract ChatEvent nextMsg(ChatEvent msg, boolean mine);
}
