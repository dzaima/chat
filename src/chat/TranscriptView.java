package chat;

public abstract class TranscriptView {
  public abstract void older();
  public abstract void newer();
  public abstract Chatroom room();
  
  protected boolean open;
  public /*open*/ void show() { open = true; room().m.setCurrentName("Transcript of "+room().name); }
  public /*open*/ void hide() { open = false; }
  
  public abstract void tick();
}
