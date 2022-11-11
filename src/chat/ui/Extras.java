package chat.ui;

import chat.*;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.TextNode;

public class Extras {
  public enum LinkType { EXT, UNK, IMG, TEXT }
  public static TextNode textLink(ChatUser u, String url, LinkType img) {
    return new LinkNode(u, url, img, null);
  }
  public static TextNode textLink(ChatUser u, String url, LinkType img, byte[] data) {
    return new LinkNode(u, url, img, data);
  }
  
  public static class ClickableTextNode extends TextNode {
    public ClickableTextNode(Ctx ctx, String[] ks, Prop[] vs) { super(ctx, ks, vs); }
    public Runnable fn;
    public void mouseStart(int x, int y, Click c) { if (c.bL()) c.register(this, x, y); }
    public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
    public void mouseUp(int x, int y, Click c) { if (fn!=null) fn.run(); }
  }
  private static class LinkNode extends TextNode {
    private final ChatUser u;
    private final String url;
    private final LinkType type;
    private final byte[] data;
    public LinkNode(ChatUser u, String url, LinkType type, byte[] data) {
      super(u.m.base.ctx, new String[]{"color", "hover"}, new Prop[]{u.m.gc.getProp("chat.link.col"), EnumProp.TRUE});
      this.u = u;
      this.url = url;
      this.type = type;
      this.data = data;
    }
    public void mouseStart(int x, int y, Click c) {
      super.mouseStart(x, y, c);
      c.register(this, x, y);
    }
    
    public void mouseDown(int x, int y, Click c) {
      if (c.bR()) Popup.rightClickMenu(gc, ctx, "chat.linkMenu", cmd -> {
        switch (cmd) { default: ChatMain.warn("bad cmd " + cmd); break;
          case "(closed)":
            break;
          case "copy":
            u.m.copyString(url);
            break;
          case "openExt":
            u.openLink(url, LinkType.EXT, null);
        }
      }).takeClick(c);
    }
    
    public void mouseTick(int x, int y, Click c) {
      c.onClickEnd();
    }
    public void mouseUp(int x, int y, Click c) {
      if (visible && c.bL()) u.openLink(url, type, data);
    }
    public void hoverS() { super.hoverS(); u.m.hoverURL=url;  u.m.updInfo(); }
    public void hoverE() { super.hoverE(); u.m.hoverURL=null; u.m.updInfo(); }
  }
}
