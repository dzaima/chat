package chat.networkLog;

import chat.*;

public abstract class BasicNetworkView extends View {
  public final boolean typed(int codepoint) { return false; }
  public final String asCodeblock(String s) { return s; }
  public final LiveView baseLiveView() { return null; }
  public final boolean contains(ChatEvent ev) { return false; }
}
