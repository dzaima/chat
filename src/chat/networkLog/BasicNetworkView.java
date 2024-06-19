package chat.networkLog;

import chat.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.VlNode;
import dzaima.utils.Pair;

public abstract class BasicNetworkView extends LiveView {
  protected BasicNetworkView(ChatMain m) {
    super(m);
  }
  
  public final boolean typed(int codepoint) { return false; }
  public final String asCodeblock(String s) { return s; }
  public final boolean contains(ChatEvent ev) { return false; }
  
  public MuteState muteState() { return MuteState.UNMUTED; }
  public Pair<Integer, Boolean> unreadInfo() { return new Pair<>(0, false); }
  public ChatEvent prevMsg(ChatEvent msg, boolean mine) { return null; }
  public ChatEvent nextMsg(ChatEvent msg, boolean mine) { return null; }
  public void older() { }
  public Node inputPlaceContent() { return new VlNode(m.ctx, Props.none()); }
  public void post(String raw, String replyTo) { }
  public void edit(ChatEvent m, String raw) { }
  public void upload() { }
  public void mentionUser(String uid) { }
  public void markAsRead() { }
}
