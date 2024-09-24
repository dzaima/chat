package chat;

import chat.mx.MediaThread;
import chat.ui.*;
import chat.utils.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.utils.*;
import dzaima.utils.JSON.Obj;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.*;

public abstract class ChatUser {
  public final ChatMain m;
  public final Node node;
  public final RoomListNode roomListNode; // list of rooms
  // public final MuteState muteState = new MuteState();
  
  public ChatUser(ChatMain m) {
    this.m = m;
    node = m.accountNode.ctx.make(m.gc.getProp("chat.rooms.accountP").gr());
    roomListNode = (RoomListNode) node.ctx.id("roomlist");
    roomListNode.u = this;
    ((AccountNode) node.ctx.id("account")).u = this;
  }
  
  public abstract Vec<? extends Chatroom> rooms();
  public abstract void saveRooms();
  
  public abstract void tick();
  public abstract void close();
  public abstract String id();
  public abstract Obj data(); // must return a proper save&restore-ready result at any point of time after constructor has finished
  
  public static abstract class URIInfo {
    public final String uri;
    public final Obj obj;
    public final boolean safe, hasThumbnail;
    public abstract MediaThread.MediaRequest requestFull();
    public abstract MediaThread.MediaRequest requestThumbnail();
    public URIInfo(String uri, Obj obj, boolean safe, boolean hasThumbnail) { this.uri = uri; this.obj = obj; this.safe = safe; this.hasThumbnail = hasThumbnail; }
  }
  
  public /*open*/ String linkMenu(String url) { return "chat.linkMenu"; }
  public abstract URIInfo parseURI(String src, Obj info); // result.uri == src
  public abstract void loadImg(URIInfo info, boolean acceptThumbnail,
                               Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, Supplier<Boolean> stillNeeded);
  public abstract void openLink(String url, Extras.LinkInfo info);
  
  
  
  public void downloadTo(String url, Path p, Runnable onOk) {
    Pair<byte[], Boolean> r = parseURI(url, null).requestFull().requestHere();
    if (r.a!=null) {
      try {
        Files.write(p, r.a);
        onOk.run();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
  
  public void downloadToSelect(String url, Extras.LinkInfo info, Runnable onOk) {
    m.saveFile(null, null, info.expectedFilename(), path -> {
      if (path!=null) downloadTo(url, path, onOk);
    });
  }
  
  public void downloadTmpAndOpen(String url, Extras.LinkInfo info, Runnable onOk) {
    try {
      String post = info.expectedFilename();
      Path path = Files.createTempFile("temp-", post.isEmpty()? null : "-"+post);
      downloadTo(url, path, () -> {
        m.gc.openFile(path);
        onOk.run();
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  
  
  public int userCol(String name, boolean mine, boolean pill) {
    if (mine) return pill? m.colMyPill : m.colMyNick;
    int[] cs = pill? m.colOtherPills : m.colOtherNicks;
    return cs[(name.hashCode()&Integer.MAX_VALUE) % cs.length];
  }
  
  public void preRoomListChange() {
    if (roomListNode.reordering()) roomListNode.stopReorder(false);
  }
  public void roomListChanged() {
    // TODO undo/redo
    assert !roomListNode.reordering();
    roomListNode.recalculateDepths();
    saveRooms();
  }
  
  public void updateFolderUnread() {
    for (Node c : roomListNode.ch) updateFolderUnread(c);
  }
  private void updateFolderUnread(Node c) {
    if (c instanceof RoomListNode.DirStartNode) {
      RoomListNode.DirStartNode d = (RoomListNode.DirStartNode) c;
      if (!d.isOpen()) {
        Vec<Chatroom> rs = d.subRooms();
        boolean ping = false;
        int count = 0;
        for (Chatroom r : rs) {
          UnreadInfo s = r.muteState.info();
          count+= s.unread;
          ping|= s.ping;
        }
        d.unread = count;
        d.ping = ping;
      }
      d.updateUnread();
    }
  }
}
