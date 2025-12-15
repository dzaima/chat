package chat.ui;

import chat.*;
import chat.utils.UnreadInfo;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.*;
import dzaima.utils.*;

public class RoomListNode extends ReorderableNode {
  public ChatUser u;
  
  public RoomListNode(Ctx ctx, Props props) {
    super(ctx, props);
  }
  
  public int findRoomClosest(Chatroom r) {
    for (int i = 0; i < ch.sz; i++) {
      Node n = ch.get(i);
      if (containsRoom(n, r)) return i;
    }
    return -1;
  }
  
  private static boolean containsRoom(Node n, Chatroom r) {
    if (n instanceof RoomNode && ((RoomNode) n).r==r) return true;
    if (n instanceof DirStartNode && !((DirStartNode) n).isOpen()) {
      for (Node c : ((DirStartNode) n).closedCh) if (containsRoom(c, r)) return true;
    }
    return false;
  }
  
  public Chatroom nextRoom(int start, int delta) {
    int i = start+delta;
    while (delta==1? i<ch.sz : i>=0) {
      Node c = ch.get(i);
      if (c instanceof RoomListNode.RoomNode) return ((RoomListNode.RoomNode) c).r;
      i+= delta;
    }
    return null;
  }
  
  
  
  public boolean shouldReorder(int idx, Node n) {
    return n instanceof RoomNode || n instanceof DirStartNode; // also excludes the placeholder before room list is loaded
  }
  
  private static final Props DIR_V = Props.of("dir", EnumProp.cache("v"));
  public Node reorderSelect(Node sel) {
    if (!(sel instanceof DirStartNode)) return sel;
    DirStartNode s = (DirStartNode) sel;
    int[] r;
    if (s.isOpen()) {
      r = s.getRange();
    } else {
      int p = s.getPos();
      r = new int[]{p, p+1};
    }
    
    PackedListNode l = new PackedListNode(ctx, DIR_V);
    Vec<Node> dir = Vec.ofReuse(ch.get(r[0], r[1], Node[].class));
    
    remove(r[0], r[1]);
    l.insert(0, dir);
    insert(r[0], l);
    return l;
  }
  
  public void reorderStarted(Node n) {
    if (n instanceof RoomNode) ((RoomNode) n).updBG();
    else if (n instanceof PackedListNode) for (Node c : n.ch) ((RoomEntryNode) c).updBG();
    recalculateDepths();
  }
  
  public void reorderSwapped() {
    recalculateDepths();
    noFancyScroll(); // TODO move to base ReorderableNode?
  }
  
  public void noFancyScroll() {
    ScrollNode s = ScrollNode.nearestScrollNode(this);
    if (s==null) return;
    s.ignoreFocus();
    s.ignoreYE();
  }
  
  public boolean holdingRoom(RoomEntryNode r) {
    Node h = heldNode();
    if (h==null) return false;
    if (h == r) return true;
    if (h instanceof PackedListNode) return h.ch.get(0)==r;
    return false;
  }
  
  public void reorderEnded(int oldIdx, int newIdx, Node n) {
    if (n instanceof RoomNode) {
      ((RoomNode) n).updBG();
    }
    if (n instanceof PackedListNode) {
      int i = ch.indexOf(n);
      remove(i, i+1);
      insert(i, n.ch);
      for (Node c : n.ch) ((RoomEntryNode) c).updBG();
    }
    u.roomListChanged();
  }
  
  
  public void recalculateDepths() {
    calculateDepths(ch, 0);
    noFancyScroll();
  }
  public void calculateDepths(Vec<Node> l, int depth) {
    for (Node n : l) {
      boolean placeholder = false;
      if (n instanceof ReorderableNode.PlaceholderNode) {
        n = ((PlaceholderNode) n).n;
        if (n instanceof PackedListNode) {
          calculateDepths(n.ch, depth);
        }
        placeholder = true;
      }
      if (n instanceof DirStartNode) depth++;
      if (n instanceof RoomEntryNode) {
        RoomEntryNode e = (RoomEntryNode) n;
        if (e.depth!=depth) {
          e.depth = depth;
          e.mResize();
        }
      }
      if (placeholder) placeholderDepth = depth;
      if (n instanceof DirStartNode && !((DirStartNode) n).isOpen()) depth--;
      if (n instanceof DirEndNode) depth--;
    }
  }
  public static void drawDepths(Graphics g, ChatUser u, int h, int depth, int mode) { // 0:middle 1:end 2:start&end
    if (depth==0) return;
    int[] cols = u.m.folderColors;
    int w = u.m.gc.getProp("chat.folder.indentW").len();
    int x = 0;
    if (mode==1) depth--;
    for (int i = 0; i < depth; i++) {
      g.rect(x, 0, x+w, i==depth-1 && mode==2? h-u.m.gc.getProp("chat.folder.endH").len() : h, cols[i%cols.length]);
      x+= w;
    }
  }
  int placeholderDepth;
  public void drawPlaceholder(Graphics g, int w, int h) {
    drawDepths(g, u, h, placeholderDepth, 0);
  }
  
  public static abstract class RoomEntryNode extends Node {
    public final ChatUser u;
    public int depth;
    public RoomEntryNode(ChatUser u, Node ch) {
      super(ch.ctx, Props.none());
      this.u = u;
      add(ch);
    }
    public void propsUpd() { super.propsUpd(); updBG(); }
    
    boolean hovered, openMenu;
    int bg = 0;
    public void updBG() {
      boolean showHover = (hovered && !u.roomListNode.reordering())  ||  u.roomListNode.holdingRoom(this)  ||  openMenu;
      int newbg = isSelected()? gc.getProp("chat.room.selected").col() : showHover? gc.getProp("chat.room.hovered").col() : 0; // TODO plain background when drag'n'dropping outside
      if (newbg!=bg) {
        bg = newbg;
        mRedraw();
      }
    }
    public abstract boolean isSelected();
    
    public void bg(Graphics g, boolean full) {
      if (Tools.st(bg)) pbg(g, full);
      if (Tools.vs(bg)) g.rect(0, 0, w, h, bg);
      drawDepths(g, u, h, depth, this instanceof DirEndNode? 1 : this instanceof DirStartNode && !((DirStartNode) this).isOpen()? 2 : 0);
    }
    
    public int indent() { return depth*gc.getProp("chat.folder.indentW").len(); }
    public int minW() { return ch.get(0).minW()+indent(); }
    public int maxW() { return ch.get(0).maxW()+indent(); }
    public int minH(int w) { return ch.get(0).minH(w-indent()); }
    public int maxH(int w) { return ch.get(0).maxH(w-indent()); }
    public void resized() { ch.get(0).resize(w-indent(), h, indent(), 0); }
  }
  public static abstract class SelRoomEntryNode extends RoomEntryNode {
    public SelRoomEntryNode(ChatUser u, Node ch) { super(u, ch); }
    
    
    public void hoverS() { hovered=true;  updBG(); ctx.vw().pushCursor(Window.CursorType.HAND); }
    public void hoverE() { hovered=false; updBG(); ctx.vw().popCursor(); }
    
    public void mouseStart(int x, int y, Click c) {
      super.mouseStart(x, y, c);
      if (c.bL() || c.bR()) c.register(this, x, y);
    }
    
    public void mouseDown(int x, int y, Click c) {
      if (c.bR()) {
        openMenu = true;
        rightClick(c, x, y, () -> { openMenu=false; updBG(); });
      }
    }
    public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
    public void mouseUp(int x, int y, Click c) { if (visible) leftClick(); }
    public abstract void leftClick();
    public abstract void rightClick(Click c, int x, int y, Runnable onClose);
  }
  
  public static abstract class ExternalDirInfo {
    public DirStartNode node;
    public abstract void addToMenu(PartialMenu pm);
    public abstract void setLocalName(String val);
    public abstract void nodeAttached();
  }
  public static class DirStartNode extends SelRoomEntryNode {
    public final ExternalDirInfo external;
    public final RoomEditing editor;
    private final Node nameObj;
    public String rawName;
    public boolean isDMs;
    
    public int unread;
    public boolean ping;
    
    public Node[] closedCh;
    
    public DirStartNode(ChatUser r) {
      this(r, null);
    }
    public DirStartNode(ChatUser r, ExternalDirInfo external) {
      super(r, r.m.ctx.make(r.m.gc.getProp("chat.rooms.roomP").gr()));
      editor = new RoomEditing(u, "chat.rooms.rename.folderField") {
        protected String getName() { return getTitle(); }
        protected Node entryPlace() { return ch.get(0).ctx.id("entryPlace"); }
        protected void rename(String newName) {
          if (external!=null) external.setLocalName(newName);
          else setName(newName);
        }
      };
      this.external = external;
      nameObj = ctx.make(gc.getProp("chat.rooms.folderName").gr());
      ch.get(0).ctx.id("name").replace(0, nameObj);
      setName(gc.getProp("chat.folder.defaultName").str());
      if (external!=null) {
        external.node = this;
        external.nodeAttached();
      }
    }
    
    public String getTitle() {
      return rawName!=null? rawName : gc.getProp("chat.folder.defaultName").str();
    }
    public void setName(String name) {
      this.rawName = name;
      if (editor.editing()) return;
      nameObj.ctx.id("name").replace(0, new StringNode(ctx, (isOpen()? "" : "["+subRooms().sz+"] ") + getTitle()));
    }
  
    public void updateUnread() {
      if (editor.editing()) return;
      RoomListNode.setUnread(ch.get(0), MuteState.UNMUTED, isOpen()? UnreadInfo.NONE : new UnreadInfo(unread, ping));
    }
    
    public boolean isOpen() {
      return closedCh==null;
    }
    public void close(int s, int e) {
      assert isOpen();
      closedCh = p.ch.get(s+1, e, Node[].class);
      p.remove(s+1, e);
      setName(rawName);
      u.updateFolderUnread();
    }
    public void open() {
      assert !isOpen();
      p.insert(getPos()+1, Vec.ofReuse(closedCh));
      closedCh = null;
      setName(rawName);
      u.updateFolderUnread();
    }
    public void leftClick() {
      u.preRoomListChange();
      if (isOpen()) {
        int[] r = getRange();
        close(r[0], r[1]);
      } else {
        open();
      }
      u.roomListChanged();
    }
    public Vec<Chatroom> subRooms() {
      assert !isOpen();
      Vec<Chatroom> res = new Vec<>();
      for (Node n : closedCh) recRooms(res, n);
      return res;
    }
    public void recRooms(Vec<Chatroom> res, Node c) {
      if (c instanceof RoomNode) res.add(((RoomNode) c).r);
      if (c instanceof DirStartNode && !((DirStartNode) c).isOpen()) {
        for (Node n : ((DirStartNode) c).closedCh) recRooms(res, n);
      }
    }
    
    public int getPos() {
      return p.ch.indexOf(this);
    }
    public int[] getRange() {
      assert isOpen();
      int s = getPos();
      int e = s+1;
      int d = 1;
      Vec<Node> pch = p.ch;
      do {
        Node c = pch.get(e);
        if (c instanceof DirStartNode && ((DirStartNode) c).isOpen()) d++;
        else if (c instanceof DirEndNode) d--;
        e++;
      } while (d != 0);
      return new int[]{s, e};
    }
    public boolean isSelected() { return false; }
    
    public void rightClick(Click c, int x, int y, Runnable onClose) {
      PartialMenu pm = new PartialMenu(gc);
      pm.add(gc.getProp("chat.roomMenu.folder").gr(), "wrap", () -> DirStartNode.wrap(u, this));
      
      if (external==null) {
        pm.add(gc.getProp("chat.roomMenu.localFolder").gr(), s -> {
          switch (s) {
            case "rename": editor.startEdit(); return true;
            case "delete":
              if (!isOpen()) open();
              int[] r = getRange();
              r[1]--;
              u.preRoomListChange();
              u.roomListNode.remove(r[1], r[1] + 1);
              u.roomListNode.remove(r[0], r[0] + 1);
              u.roomListChanged();
              return true;
            default: return false;
          }
        });
      } else {
        external.addToMenu(pm);
      }
      
      pm.open(ctx, c, onClose);
    }
    
    public static void wrap(ChatUser u, Node n) {
      int s = u.roomListNode.ch.indexOf(n);
      if (s==-1) return;
      int e = s+1;
      if (n instanceof DirStartNode && ((DirStartNode) n).isOpen()) e = ((DirStartNode) n).getRange()[1];
      u.preRoomListChange();
      u.roomListNode.insert(e, new DirEndNode(u));
      u.roomListNode.insert(s, new DirStartNode(u));
      u.roomListChanged();
    }
  }
  public static class DirEndNode extends RoomEntryNode {
    public DirEndNode(ChatUser r) { super(r, r.m.ctx.make(r.m.gc.getProp("chat.rooms.folderEnd").gr())); }
    public boolean isSelected() { return false; }
  }
  
  public static class RoomNode extends SelRoomEntryNode {
    public final Chatroom r;
    public RoomNode(Chatroom r, ChatUser u) {
      super(u, r.m.ctx.make(r.m.gc.getProp("chat.rooms.roomP").gr()));
      this.r = r;
    }
    
    public void propsUpd() { super.propsUpd(); updBG(); }
    
    public boolean isSelected() { return r.mainView().open; }
    
    public void leftClick() { r.m.toRoom(r.mainView()); }
    public void rightClick(Click c, int x, int y, Runnable onClose) { r.roomMenu(c, x, y, onClose); }
  }
  
  public static void setUnread(Node node, MuteState muteState, UnreadInfo u) {
    Node un = node.ctx.id("unread");
    un.clearCh();
    if (muteState.hidden(u)) {
      un.add(node.ctx.make(node.ctx.gc.getProp("chat.rooms.unreadHiddenP").gr()));
    } else if (u.any()) {
      un.add(makeUnread(node.ctx, u));
    }
  }
  
   public static Node makeUnread(Ctx ctx, UnreadInfo u) {
    Node n = ctx.make(ctx.gc.getProp("chat.rooms.unreadP").gr());
    n.ctx.id("num").add(new StringNode(n.ctx, "("+(u.unread>0? u.unread+"":"")+(u.ping? "*" : "")+")"));
    return n;
  }
}
