package chat.ui;

import chat.*;
import dzaima.ui.eval.PNodeGroup;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.*;
import dzaima.ui.node.types.editable.*;
import dzaima.utils.Vec;

public class RoomListNode extends ReorderableNode {
  public ChatUser u;
  
  public RoomListNode(Ctx ctx, String[] ks, Prop[] vs) {
    super(ctx, ks, vs);
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
    
    PackedListNode l = new PackedListNode(ctx, new String[]{"dir"}, new Prop[]{new EnumProp("v")});
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
    s.ignoreFocus(true);
    s.ignoreEnd();
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
      super(ch.ctx, KS_NONE, VS_NONE);
      this.u = u;
      add(ch);
    }
    
    
    boolean hovered, openMenu;
    public void updBG() {
      Node bg = ctx.id("bg");
      boolean showHover = (hovered && !u.roomListNode.reordering())  ||  u.roomListNode.holdingRoom(this)  ||  openMenu;
      bg.set(bg.id("bg"), isSelected()? gc.getProp("chat.room.selected") : showHover? gc.getProp("chat.room.hovered") : SelRoomEntryNode.TRANSPARENT); // TODO plain background when drag'n'dropping outside
    }
    public abstract boolean isSelected();
    
    public void over(Graphics g) {
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
    private static final Prop TRANSPARENT = new ColProp(0);
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
    public void mouseUp(int x, int y, Click c) { leftClick(); }
    public abstract void leftClick();
    public abstract void rightClick(Click c, int x, int y, Runnable onClose);
  }
  
  public static abstract class ExternalDirInfo {
    public DirStartNode node;
    public abstract void addToMenu(PNodeGroup gr);
    public abstract void runAction(String cmd);
    public abstract void setLocalName(String val);
    public abstract void nodeAttached();
  }
  public static class DirStartNode extends SelRoomEntryNode {
    public final ExternalDirInfo external;
    Node nameObj;
    public String name;
    public Node afterEditReplacement;
    
    public Node[] closedCh;
    
    public DirStartNode(ChatUser r) {
      this(r, null);
    }
    public DirStartNode(ChatUser r, ExternalDirInfo external) {
      super(r, r.m.ctx.make(r.m.gc.getProp("chat.rooms.roomP").gr()));
      this.external = external;
      nameObj = ctx.make(gc.getProp("chat.rooms.folderName").gr());
      ch.get(0).ctx.id("name").replace(0, nameObj);
      setName(gc.getProp("chat.folder.defaultName").str());
      if (external!=null) {
        external.node = this;
        external.nodeAttached();
      }
    }
    
    public boolean editing() {
      return afterEditReplacement!=null;
    }
    public void setName(String name) {
      this.name = name;
      if (editing()) return;
      String disp = name!=null? name : gc.getProp("chat.folder.defaultName").str();
      nameObj.ctx.id("name").replace(0, new StringNode(ctx, (isOpen()? "" : "["+subRooms().sz+"] ") + disp));
    }
    private void startEdit() {
      if (editing()) return;
      Node rename = ctx.make(gc.getProp("chat.rooms.folderRename.field").gr());
      TextFieldNode f = (TextFieldNode) rename.ctx.id("val");
      f.setFn(i -> {
        if (i!=-1) setName(f.getAll());
        endEdit();
        return true;
      });
      f.append(name);
      Node e = ch.get(0).ctx.id("entryPlace");
      afterEditReplacement = e.ch.get(0);
      e.replace(0, rename);
      ctx.win().focus(f);
    }
    private void endEdit() {
      u.preRoomListChange();
      ch.get(0).ctx.id("entryPlace").replace(0, afterEditReplacement);
      afterEditReplacement = null;
      if (external!=null) external.setLocalName(name.length()==0? null : name);
      else setName(name);
      u.roomListChanged();
    }
    public static class NameEditFieldNode extends TextFieldNode {
      public NameEditFieldNode(Ctx ctx, String[] ks, Prop[] vs) { super(ctx, ks, vs); }
      
      public int action(Key key, KeyAction a) {
        switch (gc.keymap(key, a, "chat.rooms.folderRename")) {
          default: return super.action(key, a);
          case "cancel":
            fn.test(-1);
            return 1;
        }
      }
    }
    
    public boolean isOpen() {
      return closedCh==null;
    }
    public void close(int s, int e) {
      assert isOpen();
      closedCh = p.ch.get(s+1, e, Node[].class);
      p.remove(s+1, e);
      setName(name);
    }
    public void open() {
      assert !isOpen();
      p.insert(getPos()+1, Vec.ofReuse(closedCh));
      closedCh = null;
      setName(name);
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
      PNodeGroup gr = gc.getProp("chat.folderMenu.main").gr().copy();
      if (external ==null) {
        gr.ch.addAll(gc.getProp("chat.folderMenu.local").gr().ch);
      } else {
        external.addToMenu(gr);
      }
      
      Popup.rightClickMenu(gc, ctx, gr, cmd -> {
        if (u.roomListNode.reordering() && !"(closed)".equals(cmd)) return;
        switch (cmd) {
          default:
            if (external !=null) external.runAction(cmd);
            else ChatMain.warn("Unknown menu option "+cmd);
            break;
          case "(closed)":
            onClose.run();
            break;
          case "wrap":
            DirStartNode.wrap(u, this);
            break;
          case "delete":
            if (!isOpen()) open();
            int[] r = getRange();
            r[1]--;
            u.preRoomListChange();
            u.roomListNode.remove(r[1], r[1]+1);
            u.roomListNode.remove(r[0], r[0]+1);
            u.roomListChanged();
            break;
          case "rename":
            startEdit();
            break;
        }
      }).takeClick(c);
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
    
    boolean openMenu;
    public void openMenu(boolean v) { openMenu = v; updBG(); }
    public boolean isSelected() { return r.open; }
    
    public void leftClick() { r.m.toRoom(r); }
    public void rightClick(Click c, int x, int y, Runnable onClose) { r.roomMenu(c, x, y, onClose); }
  }
}
