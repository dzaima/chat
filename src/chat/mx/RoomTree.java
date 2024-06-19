package chat.mx;

import chat.ui.RoomListNode;
import dzaima.ui.node.Node;
import dzaima.utils.*;
import dzaima.utils.JSON.Obj;

import java.util.*;

public class RoomTree {
  private final String id; // if id==null, this is a local folder
  private final String name;
  private final Obj o;
  private MxChatroom got;
  private boolean open;
  private Vec<RoomTree> ch;
  
  public RoomTree(String id, MxChatroom got, String name, Obj o) {
    this.id = id;
    this.got = got;
    this.name = name;
    this.o = o;
  }
  
  public static void restoreTree(MxChatUser u, JSON.Arr state, JSON.Arr legacyOrder) { // won't request to save
    HashMap<String, MxChatroom> allRooms = u.roomMap;
    
    HashMap<String, RoomTree> map = new HashMap<>();
    Vec<RoomTree> root = new Vec<>();
    for (Obj o : state.objs()) root.add(buildTree(map, o)); // build target tree
    
    HashSet<String> knownSpaces = new HashSet<>();
    allRooms.forEach((k, v) -> {
      if (v.asDir()!=null) knownSpaces.add(k);
    });
    keepOnlyKnown(root, allRooms.keySet(), knownSpaces); // remove tree entries that aren't matched to any live room, and properly mark what is and isn't a folder; map can stay as-is, it isn't iterated over
    
    HashMap<String, MxChatroom> roomsLeft = new HashMap<>();
    HashMap<String, MxChatroom> spacesLeft = new HashMap<>();
    HashMap<String, String> toSpace = new HashMap<>();
    allRooms.forEach((k, v) -> { // collect rooms & spaces that don't have a stored target position
      if (v.asDir()!=null) for (String child : v.spaceInfo.children) toSpace.put(child, k);
      
      if (map.containsKey(k)) {
        RoomTree t = map.get(k);
        t.got = v;
        v.muteState.deserialize(t.o.str("mute", ""));
        if (t.o.has("name")) v.setCustomName(t.o.str("name"));
      } else {
        if (v.spaceInfo!=null) spacesLeft.put(k, v);
        else roomsLeft.put(k, v);
      }
    });
    
    spacesLeft.forEach((k, v) -> { // add new spaces
      RoomTree t = new RoomTree(k, v, null, Obj.E);
      t.open = true;
      t.ch = new Vec<>();
      root.add(t);
      map.put(k, t);
    });
    
    roomsLeft.forEach((k, v) -> { // add new rooms
      String spaceId = toSpace.get(k);
      RoomTree t = new RoomTree(k, v, null, Obj.E);
      map.put(k, t);
      if (spaceId!=null) {
        RoomTree space = map.get(spaceId);
        assert space!=null;
        space.ch.add(t);
      } else {
        root.add(t);
      }
    });
    
    if (legacyOrder!=null && legacyOrder.size()>0) { // optionally reorder things by legacy room order
      HashMap<String, Integer> order = new HashMap<>();
      for (String c : legacyOrder.strs()) order.put(c, order.size());
      orderBy(root, order);
    }
    
    u.roomListNode.clearCh(); // and save all changes to the GUI room list
    for (RoomTree c : root) addToList(u, c);
    u.roomListNode.recalculateDepths();
  }
  
  public static RoomTree buildTree(HashMap<String, RoomTree> map, Obj o) {
    RoomTree res = new RoomTree(o.str("id", null), null, o.str("name", null), o);
    res.open = o.bool("open", true);
    if (res.id!=null) map.put(res.id, res);
    if (o.has("folder")) {
      res.ch = new Vec<>();
      for (Obj c : o.arr("folder").objs()) res.ch.add(buildTree(map, c));
    }
    return res;
  }
  
  private static void keepOnlyKnown(Vec<RoomTree> v, Set<String> allRooms, Set<String> spaces) {
    v.filterInplace(c -> {
      if (c.id!=null && !allRooms.contains(c.id)) return false; // don't keep unknown things that aren't local folders
      
      if (c.id==null || spaces.contains(c.id)) { // keep children of known folders
        if (c.ch==null) c.ch = new Vec<>();
        keepOnlyKnown(c.ch, allRooms, spaces);
      } else {
        c.ch = null;
      }
      
      return true;
    });
  }
  
  private static void orderBy(Vec<RoomTree> c, HashMap<String, Integer> order) {
    c.sort(Comparator.comparing(v -> order.getOrDefault(v.id, Integer.MAX_VALUE)));
    for (RoomTree n : c) if (n.ch!=null) orderBy(n.ch, order);
  }
  
  private static void addToList(MxChatUser u, RoomTree t) {
    RoomListNode l = u.roomListNode;
    if (t.ch!=null) {
      RoomListNode.DirStartNode s;
      if (t.id==null) {
        s = new RoomListNode.DirStartNode(u);
        s.setName(t.name);
      } else {
        s = new RoomListNode.DirStartNode(u, t.got.asDir());
        s.external.setLocalName(t.name);
      }
      int sp = l.ch.sz;
      l.add(s);
      
      for (RoomTree c : t.ch) addToList(u, c);
      l.add(new RoomListNode.DirEndNode(u));
      
      if (!t.open) s.close(sp, l.ch.sz);
    } else {
      l.add(t.got.node);
    }
  }
  
  
  
  
  
  
  public static JSON.Arr saveTree(RoomListNode list) {
    Vec<Node> l = new Vec<>();
    for (Node c : list.ch) recAdd(l, c);
    SavingState s = new SavingState(l);
    return new JSON.Arr(saveList(s, false).toArray(new JSON.Val[0]));
  }
  public static void recAdd(Vec<Node> l, Node c) {
    l.add(c);
    if (c instanceof RoomListNode.DirStartNode) {
      RoomListNode.DirStartNode d = (RoomListNode.DirStartNode) c;
      if (!d.isOpen()) for (Node n : d.closedCh) recAdd(l, n);
    }
  }
  private static class SavingState {
    final Vec<Node> ch;
    public final int end;
    int pos;
    
    private SavingState(Vec<Node> ch) {
      this.ch = ch;
      end = ch.sz;
    }
    public Node next() {
      return ch.get(pos++);
    }
  }
  
  private static Obj saveDir(SavingState s, RoomListNode.DirStartNode e0) {
    HashMap<String, JSON.Val> m = new HashMap<>();
    if (e0.external!=null) {
      MxChatroom.SpaceInfo space = (MxChatroom.SpaceInfo) e0.external;
      m.put("id", new JSON.Str(space.r.r.rid));
      if (space.customName!=null) m.put("name", new JSON.Str(space.customName));
    } else {
      if (e0.rawName!=null) m.put("name", new JSON.Str(e0.rawName));
    }
    m.put("open", JSON.Bool.of(e0.isOpen()));
    Vec<Obj> list = saveList(s, true);
    m.put("folder", new JSON.Arr(list.toArray(new JSON.Val[0])));
    return new Obj(m);
  }
  
  private static Obj saveRoom(RoomListNode.RoomNode e) {
    HashMap<String, JSON.Val> m = new HashMap<>();
    m.put("id", new JSON.Str(((MxChatroom) e.r).r.rid));
    String mute = e.r.muteState.serialize();
    if (!mute.isEmpty()) m.put("mute", new JSON.Str(mute));
    if (e.r.customName!=null) m.put("name", new JSON.Str(e.r.customName));
    return new Obj(m);
  }
  
  private static Vec<Obj> saveList(SavingState s, boolean dir) {
    Vec<Obj> res = new Vec<>();
    while (true) {
      if (!dir && s.pos == s.end) return res;
      Node c = s.next();
      
      if (c instanceof RoomListNode.DirStartNode) res.add(saveDir(s, (RoomListNode.DirStartNode) c));
      else if (c instanceof RoomListNode.RoomNode) res.add(saveRoom(((RoomListNode.RoomNode) c)));
      else if (c instanceof RoomListNode.DirEndNode) {
        if (!dir) throw new RuntimeException("Didn't expect directory end node");
        return res;
      }
    }
  }
}
