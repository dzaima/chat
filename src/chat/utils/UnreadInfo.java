package chat.utils;

public class UnreadInfo {
  public static final UnreadInfo NONE = new UnreadInfo(0, false);
  public final int unread;
  public final boolean ping;
  
  public UnreadInfo(int unread, boolean ping) {
    this.unread = unread;
    this.ping = ping;
  }
  
  public boolean none() {
    return unread==0 && !ping;
  }
  
  public boolean any() {
    return !none();
  }
}
