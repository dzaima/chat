package chat;

import chat.ui.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.*;
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
  
  protected Chatroom(ChatUser u) {
    this.m = u.m;
    node = new RNode(this, u.node.ctx.make(u.m.gc.getProp("chat.rooms.roomP").gr()));
    setName("Unnamed room");
  }
  
  
  private static final Prop TRANSPARENT = new ColProp(0);
  
  public abstract void upload();
  
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
    boolean hovered;
    public void hoverS() { hovered=true;  updBg(); ctx.vw().pushCursor(Window.CursorType.HAND); }
    public void hoverE() { hovered=false; updBg(); ctx.vw().popCursor(); }
    
    
    private int drState=2; // 0 - not started; 1 - dragging; 2 - canceled
    private int drStartY; // my original index
    private int drSelY; // current index of empty
    private int drDrawY; // y position where to draw
    private int drDY0; // thing for mouse y tracking
    private boolean drSel; // whether to reorder
    
    public void mouseStart(int x, int y, Click c) {
      c.register(this, x, y);
    }
    
    public void mouseDown(int x, int y, Click c) {
      drState = 0;
    }
    
    public void mouseTick(int x, int y, Click c) {
      ChatUser u = user();
      if (drState==0 && !gc.isClick(c)) {
        drState = 1;
        drDY0 = dy;
        // if (y<5  ) drDY0-= 5-y; // TODO maybe make this work properly
        // if (y>h-5) drDY0+= y-(h-5);
        
        u.overlapNode.draw = (overlap, g) -> {
          g.push();
          g.translate(0, drDrawY);
          bg(g, true);
          drawCh(g, true);
          g.pop();
          
        };
        drStartY = drSelY = u.listNode.ch.indexOf(this);
        u.listNode.replace(drSelY, n -> new EmptyNode(ctx, n));
        for (Chatroom r : u.rooms()) r.node.updBg();
      }
      
      if (drState==1) {
        Vec<Node> lch = u.listNode.ch;
        
        drDrawY = drDY0 + c.cy-c.sy;
        drDrawY = Math.max(drDrawY, 0);
        drDrawY = Math.min(drDrawY, u.overlapNode.h-h);
        boolean drSelN = x>=0 && x<w;
        if (drSelN!=drSel) { drSel=drSelN; updBg(); }
        
        Node n;
        if (drSelY>0 && drDrawY < (n=lch.get(drSelY-1)).dy+n.h/2) {
          u.listNode.swap(drSelY, drSelY-1);
          drSelY--;
        } else if (drSelY<lch.sz-1 && drDrawY+h > (n=lch.get(drSelY+1)).dy+n.h/2) {
          u.listNode.swap(drSelY, drSelY+1);
          drSelY++;
        }
        
        u.overlapNode.mRedraw();
      }
    }
    
    public void mouseUp(int x, int y, Click c) {
      if (drState==0) {
        m.toRoom(Chatroom.this);
      } else if (drState==1) {
        endDrag(x>=0 && x<w);
      }
    }
    public void endDrag(boolean keep) {
      if (drState!=1) return;
      drState = 2;
      ChatUser u = user();
      u.overlapNode.draw = null;
      u.listNode.replace(drSelY, n -> this);
      if (keep) {
        Vec<Chatroom> rs = new Vec<>();
        for (Node c : u.listNode.ch) rs.add(((RNode) c).r);
        u.reorderRooms(rs);
      } else {
        u.listNode.remove(drSelY, drSelY+1);
        u.listNode.insert(drStartY, this);
      }
      for (Chatroom r : u.rooms()) r.node.updBg();
    }
    
    
    public void updBg() {
      Node bg = node.ctx.id("bg");
      boolean showHover = hovered && user().overlapNode.draw==null  ||  drState==1 && drSel;
      bg.set(bg.id("bg"), open? gc.getProp("chat.room.selected") : showHover? gc.getProp("chat.room.hovered") : TRANSPARENT); // TODO plain background when drag'n'dropping outside
    }
  }
  
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
  
  public String title() {
    return name;
  }
  
  public abstract ChatUser user();
  public Chatroom room() { return this; }
  
  protected boolean open;
  public /*open*/ void show() { open=true; node.updBg(); unreadChanged(); m.setCurrentName(name); }
  public /*open*/ void hide() { open=false;node.updBg(); }
  
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
