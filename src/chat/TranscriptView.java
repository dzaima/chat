package chat;

import dzaima.ui.gui.io.*;

public abstract class TranscriptView extends View {
  public abstract void older();
  public abstract void newer();
  
  protected boolean open;
  public /*open*/ void show() { open = true; room().m.setCurrentName(title()); }
  public /*open*/ void hide() { open = false; }
  
  public String title() {
    return "Transcript of "+room().name;
  }
  
  public /*open*/ void viewTick() {
    ChatMain m = room().m;
    if (m.msgsScroll.atStart(m.endDist)) older();
    if (m.msgsScroll.atEnd(m.endDist)) newer();
  }
  
  
  public boolean key(Key key, int scancode, KeyAction a) { return false; }
  public boolean typed(int codepoint) { return false; }
}
