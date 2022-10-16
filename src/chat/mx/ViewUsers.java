package chat.mx;

import chat.ChatMain;
import chat.ui.ImageNode;
import dzaima.ui.gui.Graphics;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import libMx.MxServer;

import java.util.*;

public class ViewUsers {
  final MxChatroom r;
  final ChatMain m;
  final Node base, list;
  
  ViewUsers(MxChatroom r) {
    this.r = r;
    this.m = r.m;
    this.base = m.ctx.make(m.gc.getProp("chat.userList.ui").gr());
    this.list = base.ctx.id("list");
  }
  
  public static void viewUsers(MxChatroom r) {
    new ViewUsers(r).run();
  }
  
  public void run() {
    m.rightPanel.make("info", r::viewRoomInfo).add(base);
    Vec<Pair<String, String>> l = new Vec<>(new ArrayList<>(r.userData.keySet())).map(id -> new Pair<>(id, r.getUsername(id)));
    l.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.b, b.b));
    for (Pair<String, String> p : l) {
      MxChatroom.UserData d = r.userData.get(p.a);
      Node e = list.ctx.make(list.gc.getProp("chat.userList.entry").gr());
      if (d.avatar!=null && MxServer.isMxc(d.avatar)) e.ctx.id("image").add(new LazyLoadedImg(list.ctx, d.avatar));
      e.ctx.id("name").add(new StringNode(list.ctx, p.b));
      ((BtnNode) e.ctx.id("btn")).setFn(b -> ViewProfile.viewProfile(p.a, r));
      list.add(e);
    }
  }
  
  private class LazyLoadedImg extends Node {
    private final String mxc;
    private boolean loaded;
  
    public LazyLoadedImg(Ctx ctx, String mxc) { super(ctx, KS_NONE, VS_NONE);
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
