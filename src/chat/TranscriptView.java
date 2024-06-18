package chat;

import dzaima.ui.gui.io.*;

public abstract class TranscriptView extends View {
  public abstract void older();
  public abstract void newer();
  
  public /*open*/ void show() { room().m.setCurrentViewTitle(title()); }
  public /*open*/ void hide() { }
  
  public String title() {
    return "Transcript of "+baseLiveView().title();
  }
  
  public /*open*/ void openViewTick() {
    ChatMain m = room().m;
    if (!m.msgsScroll.ignoresYS() && m.msgsScroll.atYS(m.endDist)) older();
    if (!m.msgsScroll.ignoresYE() && m.msgsScroll.atYE(m.endDist)) newer();
  }
  
  
  public boolean key(Key key, int scancode, KeyAction a) { return false; }
  public boolean typed(int codepoint) { return false; }
  
  public abstract void highlight(ChatEvent eventID);
  
  public abstract LiveView baseLiveView(); // never null
}
