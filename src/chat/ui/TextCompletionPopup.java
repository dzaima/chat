package chat.ui;

import dzaima.ui.gui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.types.MenuNode;
import dzaima.utils.*;

class TextCompletionPopup extends Popup {
  private final int selSX, selEX, selY;
  private final ChatTextArea area;
  public NodeVW vw;
  
  public TextCompletionPopup(ChatTextArea area, int selSX, int selEX, int selY) {
    super(area.m);
    this.selSX = selSX;
    this.selY = selY;
    this.selEX = selEX;
    this.area = area;
  }
  
  protected void setup() {
    ((MenuNode) node).obj = this;
  }
  
  protected XY pos(XY size, Rect bounds) {
    return area.p.relPos(null).add(0, -size.y - area.gc.em/3);
  }
  
  protected void unfocused() { }
  
  public void stopped() {
    if (area.psP == this) {
      area.psP = null;
    }
  }
  
  protected boolean key(Key key, KeyAction a) {
    return defaultKeys(key, a);
  }
  
  public void menuItem(String id) {
    area.um.pushL("insert completion");
    area.remove(selSX, selY, selEX, selY);
    area.insert(selSX, selY, id);
    area.um.pop();
    close();
    area.focusMe();
  }
  
  public NodeVW openVW() {
    return vw = openVW(area.gc, area.ctx, area.gc.getProp("chat.userAutocompleteUI").gr(), false);
  }
}
