package chat.ui;

import chat.ChatUser;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.editable.*;

public abstract class RoomEditing {
  private final ChatUser u;
  public Node afterEditReplacement;
  private final String fieldName;
  
  public RoomEditing(ChatUser u, String name) {
    this.u = u;
    fieldName = name;
  }
  
  protected abstract String getName();
  protected abstract Node entryPlace();
  protected abstract void rename(String newName); // null means to restore to default name
  
  public void startEdit() {
    if (editing()) return;
    Node rename = u.node.ctx.make(u.node.gc.getProp(fieldName).gr());
    TextFieldNode f = (TextFieldNode) rename.ctx.id("val");
    f.setFn((a, m) -> {
      if (!editing() || (!a.done && a!=EditNode.EditAction.CUSTOM1)) return false;
      endEdit(a.enter? f.getAll() : null);
      return true;
    });
    f.append(getName());
    Node e = entryPlace();
    afterEditReplacement = e.ch.get(0);
    e.replace(0, rename);
    f.focusMe();
  }
  
  private void endEdit(String name) {
    u.preRoomListChange();
    entryPlace().replace(0, afterEditReplacement);
    afterEditReplacement = null;
    if (name!=null) rename(name.isEmpty()? null : name);
    u.roomListChanged();
  }
  
  public boolean editing() {
    return afterEditReplacement!=null;
  }
  
  
  public static class NameEditFieldNode extends TextFieldNode {
    public NameEditFieldNode(Ctx ctx, Props props) { super(ctx, props); }
    
    public int action(Key key, KeyAction a) {
      switch (gc.keymap(key, a, "chat.rooms.rename")) {
        default: return super.action(key, a);
        case "cancel":
          action(EditNode.EditAction.CUSTOM1, 0);
          return 1;
      }
    }
  }
}
