package chat.ui;

import chat.ChatMain;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;

public class RightPanel {
  public final ChatMain m;
  public final Node place;
  public final WeighedNode split;
  private float weight;
  
  public RightPanel(ChatMain m) {
    this.m = m;
    weight = 1-m.gc.getProp("chat.rightPanel.weight").f();
    place = m.ctx.id("rightPanelPlace");
    split = (WeighedNode) m.ctx.id("rightPanelWeighed");
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
  
  public float getWeight() {
    return split.isModifiable()? split.getWeight() : weight;
  }
  public void setWeight(float newW) {
    this.weight = newW;
    split.setWeight(split.isModifiable()? newW : 1);
  }
  
  void run(boolean show) {
    weight = getWeight();
    split.setModifiable(show);
    setWeight(weight);
  }
  
  public boolean isOpen() {
    return split.isModifiable();
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
