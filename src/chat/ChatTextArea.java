package chat;

import dzaima.ui.gui.io.*;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Prop;
import dzaima.ui.node.types.editable.code.CodeAreaNode;

public class ChatTextArea extends CodeAreaNode {
  public final ChatMain m;
  
  public ChatTextArea(ChatMain m, Ctx ctx, String[] ks, Prop[] vs) {
    super(ctx, ks, vs);
    this.m = m;
  }
  
  public void typed(int codepoint) {
    if (m.chatTyped(codepoint)) return;
    super.typed(codepoint);
  }
  
  public int action(Key key, KeyAction a) {
    if (m.chatKey(key, 0, a)) return 1;
    int r0 = super.action(key, a);
    if (r0==1) return r0;
    if (a.press && key.k_esc()) {
      if (m.editing !=null) { m.markEdit (null);  removeAll();  return 1; }
      if (m.replying!=null) { m.markReply(null);                return 1; }
      if (getAll().length() != 0)                { removeAll(); return 1; }
    }
    return r0;
  }
}
