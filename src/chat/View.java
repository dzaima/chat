package chat;

import dzaima.ui.gui.io.*;

public abstract class View {
  public abstract Chatroom room();
  public /*open*/ View getSearch() { return null; }
  
  public abstract void openViewTick();
  public abstract void show();
  public abstract void hide();
  
  public abstract String title();
  
  
  public abstract boolean navigationKey(Key key, KeyAction a); // runs before input
  public /*open*/ boolean actionKey(Key key, KeyAction a) { // runs after input
    return room().m.transferToInput(key, a, room().m.input());
  }
  public abstract boolean typed(int codepoint);
  
  public abstract String asCodeblock(String s);
  
  public abstract LiveView baseLiveView(); // may be null
  
  public abstract boolean contains(ChatEvent ev);
}
