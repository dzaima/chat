package chat;

import chat.ui.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.prop.*;

public abstract class LiveView extends View {
  public final ChatMain m;
  public ChatTextArea input;
  public int unread;
  public boolean ping;
  
  protected LiveView(ChatMain m) {
    this.m = m;
  }
  protected void initCompete() {
    input = new ChatTextArea(this, Props.keys("family", "numbering").values(new StrProp("Arial"), EnumProp.FALSE));
    input.wrap = true;
    cfgUpdated();
  }
  
  
  public final LiveView baseLiveView() { return this; }
  public abstract MuteState muteState();
  
  public void changeUnread(int addUnread, boolean addPing) {
    if (addUnread==0 && !addPing) return;
    if (unread==0 && !ping) firstUnreadTime = m.gc.lastMs;
    unread+= addUnread;
    ping|= addPing;
  }
  
  public long atEndStart;
  public long firstUnreadTime;
  public void openViewTick() {
    long nowMs = m.gc.lastMs;
    if (open && m.focused && m.atEnd() && !muteState().muted) {
      long viewedMs = nowMs - atEndStart;
      if (viewedMs > m.readMinViewMs  ||  viewedMs > (nowMs-firstUnreadTime)*m.altViewMult) markAsRead();
    } else {
      atEndStart = nowMs;
    }
    
    if (m.toLast!=0) {
      if (m.toLast==3) m.toHighlight.highlight(true);
      else m.msgsScroll.toYE(m.toLast==2);
      m.toLast = 0;
    } else {
      if (m.msgsScroll.atYS(m.endDist)) older();
    }
  }
  
  public boolean open;
  public /*open*/ void show() { open=true; room().node.updBG(); room().unreadChanged(); m.setCurrentViewTitle(title()); input.roomShown(); }
  public /*open*/ void hide() { open=false;room().node.updBG(); input.roomHidden(); }
  
  
  public abstract ChatEvent prevMsg(ChatEvent msg, boolean mine);
  public abstract ChatEvent nextMsg(ChatEvent msg, boolean mine);
  
  public void cfgUpdated() {
    if (m.gc.getProp("chat.preview.enabled").b()) input.setLang(MDLang.makeLanguage(m, input));
    else input.setLang(m.gc.langs().defLang);
  }
  
  public abstract void older();
  public abstract Node inputPlaceContent();
  
  public abstract void post(String raw, String replyTo);
  public abstract void edit(ChatEvent m, String raw);
  public abstract void upload(); // TODO rename to openUpload or something?
  
  public abstract void mentionUser(String uid);
  public abstract void markAsRead();
}
