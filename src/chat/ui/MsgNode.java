package chat.ui;

import chat.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.types.WrapNode;

public class MsgNode extends WrapNode {
  public final MsgType type;
  public final ChatEvent msg;
  public final MsgBorderNode border;
  
  private final int bgId;
  
  public MsgNode(Ctx pctx, MsgType type, ChatEvent msg) {
    super(pctx, pctx.makeHere(pctx.gc.getProp("chat.msg.mainP").gr()));
    this.type = type;
    this.msg = msg;
    border = (MsgBorderNode) ctx.id("border");
    border.n = this;
    bgId = border.id("bg");
    setBG();
  }
  
  public enum MsgType { MSG, NOTICE }
  
  private boolean on, hl;
  private void setBG() {
    String abg;
    if (hl) {
      abg = "chat.msg.highlight";
    } else if (mode==0) {
      if (type==MsgType.MSG) abg = on? "chat.msg.sel" : msg.mine? "chat.msg.my" : "chat.msg.other";
      else                   abg = "chat.msg.noticeBg";
    } else if (mode==2) {
      abg = "chat.msg.reply";
    } else if (mode==1) {
      abg = "chat.msg.edit";
    } else {
      ChatMain.warn("Unexpected mode "+mode+"!");
      return;
    }
    border.setCfg(bgId, abg);
  }
  
  public static final long hlTime = 1000;
  long hlStart;
  public void highlight() {
    hlStart = System.currentTimeMillis();
    mTick();
  }
  int mode;
  public void mark(int mode) {
    this.mode = mode;
    setBG();
  }
  public void setRelBg(boolean on) {
    this.on = on;
    setBG();
  }
  
  public void mouseStart(int x, int y, Click c) {
    super.mouseStart(x, y, c);
    if (Key.alt(c.mod)) c.register(this, x, y);
  }
  public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
  public void mouseUp(int x, int y, Click c) {
    msg.room().m.markReply(msg);
  }
  
  public void tickC() {
    hl = System.currentTimeMillis()-hlStart < hlTime;
    if (hl) mTick();
    setBG();
  }
  
  
}