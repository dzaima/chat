package chat.ui;

import chat.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.types.WrapNode;
import dzaima.utils.Log;

public class MsgNode extends WrapNode {
  public final MsgType type;
  public final ChatEvent msg;
  public final MsgBorderNode border;
  
  public final boolean asContext;
  
  private MsgNode(Ctx ctx, MsgType type, ChatEvent msg, boolean asContext) {
    super(ctx, ctx.makeHere(ctx.gc.getProp("chat.msg.mainP").gr()));
    this.type = type;
    this.msg = msg;
    border = (MsgBorderNode) ctx.id("border");
    border.n = this;
    this.asContext = asContext;
    setBG();
  }
  
  public static MsgNode create(ChatEvent cm, boolean asContext) {
    MsgNode r = new MsgNode(cm.m().ctx.shadow(), cm.type(), cm, asContext);
    r.ctx.id("user").replace(0, new UserTagNode(cm.m(), cm));
    return r;
  }
  
  public enum MsgType { MSG, NOTICE }
  
  public void propsUpd() { super.propsUpd(); setBG(); }
  
  private boolean on, hl;
  private void setBG() {
    String abg;
    if (hl) {
      abg = "chat.msg.highlight";
    } else if (mode==0) {
      if (asContext) abg = msg.mine? "chat.search.ctx.my" : "chat.search.ctx.other";
      else if (type==MsgType.MSG) abg = on? "chat.msg.sel" : msg.mine? "chat.msg.my" : "chat.msg.other";
      else                        abg = "chat.msg.noticeBg";
    } else if (mode==2) {
      abg = "chat.msg.reply";
    } else if (mode==1) {
      abg = "chat.msg.edit";
    } else {
      Log.warn("chat", "Unexpected mode "+mode+"!");
      return;
    }
    border.setProp("bg", gc.getProp(abg));
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
    if (Key.alt(c.mod0)) c.register(this, x, y);
  }
  public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
  public void mouseUp(int x, int y, Click c) {
    if (visible) {
      LiveView v = msg.m().liveView();
      if (v!=null) v.input.markReply(msg);
    }
  }
  
  public void tickC() {
    hl = System.currentTimeMillis()-hlStart < hlTime;
    if (hl) mTick();
    setBG();
  }
  
  
}