package chat.ui;

import chat.ChatMain;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.types.editable.code.CodeAreaNode;
import dzaima.utils.Rect;

public class ViewSource extends Popup {
  private final ChatMain m;
  private final String s;
  
  public ViewSource(ChatMain m, String s) {
    super(m);
    this.m = m;
    this.s = s;
  }
  
  protected void unfocused() { if (isVW) close(); }
  protected Rect fullRect() { return centered(m.base.ctx.vw(), 0.8, 0.8); }
  protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a); }
  
  protected void setup() {
    CodeAreaNode e = (CodeAreaNode) node.ctx.id("src");
    e.append(s);
    e.setLang(m.gc.langs().fromName("java"));
    e.um.clear();
  }
  
  public void open() {
    openWindow(m.gc, m.ctx, m.gc.getProp("chat.sourceUI").gr(), "Message source");
  }
}
