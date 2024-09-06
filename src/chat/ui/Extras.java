package chat.ui;

import chat.ChatUser;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.TextNode;
import dzaima.utils.*;

public class Extras {
  public enum LinkType { EXT, UNK, IMG, TEXT }
  public static String EXPECTED_FILENAME = "dzaima.expected_filename";
  public static class LinkInfo {
    public static final LinkInfo UNK = new LinkInfo(LinkType.UNK);
    public final LinkType type;
    public final byte[] linkedData;
    public final JSON.Obj infoObj;
    
    public LinkInfo(LinkType type, byte[] data, JSON.Obj obj) {
      this.type = type;
      linkedData = data;
      infoObj = obj;
    }
    
    public LinkInfo(LinkType type) {
      this(type, null, null);
    }
    
    public String expectedFilename() {
      return infoObj==null? "" : infoObj.str(EXPECTED_FILENAME, "");
    }
    public String mime() { // empty string if unknown
      return infoObj==null? "" : infoObj.str("mimetype","");
    }
  }
  public static TextNode textLink(ChatUser u, String url, LinkType type) {
    return new LinkNode(u, url, new LinkInfo(type));
  }
  public static TextNode textLink(ChatUser u, String url, LinkInfo info) {
    return new LinkNode(u, url, info);
  }
  
  public static class ClickableTextNode extends TextNode {
    public ClickableTextNode(Ctx ctx, Props props) { super(ctx, props); }
    public Runnable fn;
    public void mouseStart(int x, int y, Click c) { if (c.bL()) c.register(this, x, y); }
    public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
    public void mouseUp(int x, int y, Click c) { if (fn!=null) fn.run(); }
  }
  private static class LinkNode extends TextNode {
    private final ChatUser u;
    private final String url;
    private final LinkInfo info;
    private static final Props.Gen KEYS = Props.keys("color", "hover");
    public LinkNode(ChatUser u, String url, LinkInfo info) {
      super(u.m.base.ctx, KEYS.values(u.m.gc.getProp("chat.link.col"), EnumProp.TRUE));
      this.u = u;
      this.url = url;
      this.info = info;
    }
    public void mouseStart(int x, int y, Click c) {
      super.mouseStart(x, y, c);
      c.register(this, x, y);
    }
    
    public void mouseDown(int x, int y, Click c) {
      if (c.bR()) Popup.rightClickMenu(gc, ctx, u.linkMenu(url), cmd -> {
        switch (cmd) { default: Log.warn("chat", "bad cmd " + cmd); break;
          case "(closed)":
            break;
          case "copy": u.m.copyString(url); break;
          case "openExt": u.openLink(url, new LinkInfo(LinkType.EXT)); break;
          case "download": u.downloadToSelect(url, info, () -> {}); break;
          case "downloadTmpOpen": u.downloadTmpAndOpen(url, info, () -> {}); break;
        }
      }).takeClick(c);
    }
    
    
    
    public void mouseTick(int x, int y, Click c) {
      c.onClickEnd();
    }
    public void mouseUp(int x, int y, Click c) {
      if (visible && c.bL()) u.openLink(url, info);
    }
    public void hoverS() { super.hoverS(); u.m.hoverURL=url;  u.m.updInfo(); }
    public void hoverE() { super.hoverE(); u.m.hoverURL=null; u.m.updInfo(); }
  }
}
