package chat.ui;

import chat.ChatMain;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;

public class RightPanel {
  public final ChatMain m;
  public final Node place;
  public float weight;
  
  public RightPanel(ChatMain m) {
    this.m = m;
    weight = 1-m.gc.getProp("chat.rightPanel.weight").f();
    place = m.ctx.id("rightPanelPlace");
  }
  
  
  
  public void close() {
    run(false);
    place.clearCh();
  }
  public Node make(String backName, Runnable onBack) {
    m.focus(null);
    run(true);
    place.clearCh();
    Node n = m.ctx.make(m.gc.getProp("chat.rightPanel.ui").gr());
    ((BtnNode) n.ctx.id("closeBtn")).setFn(b -> close());
    
    if (backName!=null) {
      Node b = m.ctx.make(m.gc.getProp("chat.rightPanel.backBtn").gr());
      b.ctx.id("text").add(new StringNode(m.ctx, backName));
      ((BtnNode) b.ctx.id("btn")).setFn(btn -> onBack.run());
      n.ctx.id("backBtnPlace").add(b);
    }
    place.add(n);
    return n.ctx.id("content");
  }
  WeighedNode w() {
    return (WeighedNode) m.ctx.id("panelWeight");
  }
  void run(boolean show) {
    WeighedNode w = w();
    if (w.isModifiable()) weight = w.getWeight();
    w.setModifiable(show);
    w.setWeight(show? weight : 1);
  }
  
  public boolean isOpen() {
    return w().isModifiable();
  }
  
  public boolean key(Key key, KeyAction a) {
    if (!isOpen()) return false;
    
    if (m.focusNode() instanceof ChatTextArea) return false;
    switch (m.gc.keymap(key, a, "chat.rightPanel")) {
      case "close": close(); return true;
    }
    return false;
  }
}
