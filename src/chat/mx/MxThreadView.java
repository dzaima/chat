package chat.mx;

import chat.*;
import dzaima.ui.gui.io.*;

public class MxThreadView extends View {
  private final MxChatroom r;
  private final MxLog l;
  
  public MxThreadView(MxChatroom r, MxLog l) {
    this.r = r;
    this.l = l;
  }
  
  public Chatroom room() { return r; }
  
  public void viewTick() {
    
  }
  
  public void show() { l.show(); }
  public void hide() { l.hide(); }
  
  public String title() { return r.title()+" → thread"; }
  
  public boolean key(Key key, int scancode, KeyAction a) {
    return false;
  }
  
  public boolean typed(int codepoint) {
    return false;
  }
  
  public String asCodeblock(String s) {
    return r.asCodeblock(s);
  }
}
