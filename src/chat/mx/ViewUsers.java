package chat.mx;

import chat.ChatMain;
import chat.ui.*;
import dzaima.ui.gui.Graphics;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import libMx.MxServer;

import java.util.*;

public class ViewUsers {
  private final MxChatroom r;
  private final ChatMain m;
  private final Node base, list;
  private final ChatTextFieldNode search;
  private HashMap<String, MxChatroom.UserData> userData;
  
  ViewUsers(MxChatroom r) {
    this.r = r;
    this.m = r.m;
    this.base = m.ctx.make(m.gc.getProp("chat.userList.ui").gr());
    search = (ChatTextFieldNode) base.ctx.id("search");
    search.onModified = this::updateList;
    this.list = base.ctx.id("list");
    r.doubleUserList((data, lazy) -> {
      this.userData = data;
      updateList();
    });
  }
  
  public static void viewUsers(MxChatroom r) {
    new ViewUsers(r).run();
  }
  
  public void run() {
    m.rightPanel.make("info", r::viewRoomInfo).add(base);
    updateList();
  }
  
  public void updateList() {
    list.clearCh();
    Vec<Pair<String, String>> l = new Vec<>(new ArrayList<>(userData.keySet())).map(id -> new Pair<>(id, r.getUsername(id)));
    String search = this.search.getAll().toLowerCase();
    if (search.length()>0) l.filterInplace(c -> c.a.toLowerCase().contains(search) || c.b.toLowerCase().contains(search));
    l.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.b, b.b));
    for (Pair<String, String> p : l) {
      MxChatroom.UserData d = userData.get(p.a);
      if (d.s == MxChatroom.UserStatus.JOINED) {
        Node e = list.ctx.make(list.gc.getProp("chat.userList.entry").gr());
        if (d.avatar!=null && MxServer.isMxc(d.avatar)) e.ctx.id("image").add(new LazyLoadedImg(list.ctx, d.avatar));
        e.ctx.id("name").add(new StringNode(list.ctx, p.b));
        ((BtnNode) e.ctx.id("btn")).setFn(b -> ViewProfile.viewProfile(p.a, r));
        list.add(e);
      }
    }
  }
  
  private class LazyLoadedImg extends Node {
    private final String mxc;
    private boolean loaded;
  
    public LazyLoadedImg(Ctx ctx, String mxc) { super(ctx, Props.none());
      this.mxc = mxc;
    }
    
    public void drawC(Graphics g) {
      if (!loaded) {
        loaded = true;
        r.u.loadMxcImg(mxc, n -> {
          if (n!=null) add(n);
        }, ImageNode.UserListAvatarNode::new, w, h, MxServer.ThumbnailMode.CROP, () -> true);
      }
    }
    
    protected void resized() { if (ch.sz==1) ch.get(0).resize(w, h, 0, 0); }
  }
}
