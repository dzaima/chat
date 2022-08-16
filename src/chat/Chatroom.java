package chat;

import chat.ui.*;
import dzaima.ui.gui.Window;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.*;
import dzaima.utils.Vec;

public abstract class Chatroom extends View {
  public final ChatMain m;
  public RNode node;
  public String name;
  public int unread;
  public String typing = "";
  public boolean ping;
  
  public final ChatTextArea input;
  protected Chatroom(ChatUser u) {
    this.m = u.m;
    node = new RNode(this, u.node.ctx.make(u.m.gc.getProp("chat.rooms.roomP").gr()));
    setName("Unnamed room");
    input = new ChatTextArea(this, new String[]{"family", "numbering"}, new Prop[]{new StrProp("Arial"), EnumProp.FALSE});
    input.wrap = true;
    input.setFn(mod -> {
      if (mod==0) { m.send(); return true; }
      return false;
    });
    cfgUpdated();
  }
  
  public void cfgUpdated() {
    if (m.gc.getProp("chat.preview.enabled").b()) input.setLang(MDLang.makeLanguage(m, input));
    else input.setLang(m.gc.langs().defLang);
  }
  
  private static final Prop TRANSPARENT = new ColProp(0);
  
  public abstract void upload();
  
  public abstract String getUsername(String uid);
  
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
  public abstract Vec<UserRes> autocompleteUsers(String prefix);
  
  public class RNode extends WrapNode {
    public final Chatroom r;
    public RNode(Chatroom r, Node ch) {
      super(ch.ctx, ch);
      this.r = r;
    }
    
    public void propsUpd() { super.propsUpd(); setBG(); }
    
    boolean openMenu;
    public void openMenu(boolean v) { openMenu = v; setBG(); }
    boolean hovered;
    public void hoverS() { hovered=true;  setBG(); ctx.vw().pushCursor(Window.CursorType.HAND); }
    public void hoverE() { hovered=false; setBG(); ctx.vw().popCursor(); }
    
    public void mouseStart(int x, int y, Click c) {
      if (c.bL() || c.bR()) c.register(this, x, y);
    }
    
    public void mouseDown(int x, int y, Click c) {
      if (c.bL()) c.register(this, x, y);
      if (c.bR()) rightClick(c, x, y);
    }
    public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
    public void mouseUp(int x, int y, Click c) { m.toRoom(Chatroom.this); }
    
    public void setBG() {
      Node bg = node.ctx.id("bg");
      boolean showHover = hovered && !user().roomListNode.reordering()  ||  user().roomListNode.holding(this)  ||  openMenu;
      bg.set(bg.id("bg"), open? gc.getProp("chat.room.selected") : showHover? gc.getProp("chat.room.hovered") : TRANSPARENT); // TODO plain background when drag'n'dropping outside
    }
  }
  
  protected abstract void rightClick(Click c, int x, int y);
  
  public void viewTick() {
    if (m.toLast!=0) {
      if (m.toLast==3) m.toHighlight.highlight(true); 
      else m.msgsScroll.toLast(m.toLast==2);
      m.toLast = 0;
    } else {
      if (-m.msgsScroll.oy < m.endDist) older();
    }
  }
  
  
  public void setName(String name) {
    this.name = name;
    node.ctx.id("name").replace(0, new StringNode(node.ctx, name));
    if (open) m.setCurrentName(name);
  }
  
  public String title() { return name; }
  public abstract void viewRoomInfo();
  
  public abstract ChatUser user();
  public Chatroom room() { return this; }
  
  protected boolean open;
  public /*open*/ void show() { open=true; node.setBG(); unreadChanged(); m.setCurrentName(name); input.roomShown(); }
  public /*open*/ void hide() { open=false;node.setBG(); input.roomHidden(); }
  
  public abstract void readAll();
  public abstract void older();
  public abstract boolean highlight(String s);
  public abstract void post(String s, String target);
  public abstract void edit(ChatEvent m, String s);
  public abstract void delete(ChatEvent m);
  public abstract ChatEvent find(String id);
  
  
  
  public abstract void pinged();
  
  public boolean hiddenUnread;
  public void toggleHide() {
    hiddenUnread^= true;
    unreadChanged();
  }
  public int unread() {
    return hiddenUnread || m.globalHidden? 0 : unread;
  }
  public void unreadChanged() {
    if (open && m.focused && m.atEnd() && !m.globalHidden && !hiddenUnread) {
      if (unread!=0) readAll();
      unread = 0;
      ping=false;
    }
    Node un = node.ctx.id("unread");
    un.clearCh();
    if ((hiddenUnread || m.globalHidden) && !ping) {
      un.add(node.ctx.make(m.gc.getProp("chat.rooms.unreadHiddenP").gr()));
    } else if (unread>0 || ping) {
      Node n = node.ctx.make(m.gc.getProp("chat.rooms.unreadP").gr());
      n.ctx.id("num").add(new StringNode(n.ctx, "("+(unread>0?unread+"":"")+(ping? "*" : "")+")"));
      un.add(n);
    }
    m.unreadChanged();
  }
  
  public abstract ChatEvent prevMsg(ChatEvent msg, boolean mine);
  public abstract ChatEvent nextMsg(ChatEvent msg, boolean mine);
}
