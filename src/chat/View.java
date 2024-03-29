package chat;

import dzaima.ui.gui.io.*;

public abstract class View {
  public abstract Chatroom room();
  public /*open*/ View getSearch() { return null; }
  
  public abstract void viewTick();
  public abstract void show();
  public abstract void hide();
  
  public abstract String title();
  
  
  public abstract boolean key(Key key, int scancode, KeyAction a);
  public abstract boolean typed(int codepoint);
  
  public abstract String asCodeblock(String s);
}
