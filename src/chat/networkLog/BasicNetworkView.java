package chat.networkLog;

import chat.*;
import chat.utils.UnreadInfo;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.VlNode;
import dzaima.utils.Vec;

public abstract class BasicNetworkView extends LiveView {
  protected BasicNetworkView(ChatMain m) { super(m); }
  
  public final boolean typed(int codepoint) { return false; }
  public final boolean contains(ChatEvent ev) { return false; }
  
  public MuteState muteState() { return MuteState.UNMUTED; }
  public UnreadInfo unreadInfo() { return UnreadInfo.NONE; }
  public ChatEvent prevMsg(ChatEvent msg, boolean mine) { return null; }
  public ChatEvent nextMsg(ChatEvent msg, boolean mine) { return null; }
  public void older() { }
  public Node inputPlaceContent() { return new VlNode(m.ctx, Props.none()); }
  public boolean post(String raw, String replyTo) { return false; }
  public boolean edit(ChatEvent m, String raw) { return false; }
  public void upload() { }
  public void mentionUser(String uid) { }
  public void markAsRead() { }
  public boolean navigationKey(Key key, KeyAction a) { return false; }
  public Vec<Command> allCommands() { return Vec.of(); }
}
