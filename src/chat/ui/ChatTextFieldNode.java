package chat.ui;

import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.editable.TextFieldNode;

public class ChatTextFieldNode extends TextFieldNode {
  public ChatTextFieldNode(Ctx ctx, Props props) {
    super(ctx, props);
  }
  
  public Runnable onUnfocus, onModified;
  public void focusE() {
    super.focusE();
    if (onUnfocus!=null) onUnfocus.run();
  }
  public void onModified() {
    super.onModified();
    if (onModified!=null) onModified.run();
  }
}
