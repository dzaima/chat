package chat.ui;

import chat.*;
import chat.mx.*;
import dzaima.ui.gui.NodeVW;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.MenuNode;
import dzaima.ui.node.types.editable.Cursor;
import dzaima.ui.node.types.editable.code.CodeAreaNode;
import dzaima.utils.*;

import java.util.*;

public class ChatTextArea extends CodeAreaNode {
  public final ChatMain m;
  public final LiveView v;
  public ChatEvent editing;
  public ChatEvent replying;
  
  public ChatTextArea(LiveView v, Props props) {
    super(v.room().m.ctx, props);
    this.v = v;
    this.m = v.room().m;
  }
  
  public boolean keyF2(Key key, int scancode, KeyAction a) {
    String name = gc.keymap(key, a, "chat");
    switch (name) {
      case "send":
        m.send();
        return true;
      case "editUp": case "editDn":
        if (m.view instanceof LiveView && (editing==null? getAll().isEmpty() : getAll().equals(editing.getSrc())&&um.us.sz==0)) {
          LiveView v = (LiveView) m.view;
          setEdit(name.equals("editUp")? v.prevMsg(editing, true) : v.nextMsg(editing, true));
          return true;
        }
        break;
      case "replyUp": case "replyDn":
        if (m.view instanceof LiveView && editing==null) {
          LiveView v = (LiveView) m.view;
          markReply(name.equals("replyUp")? v.prevMsg(replying, false) : v.nextMsg(replying, false));
          return true;
        }
        break;
      case "pasteCode":
        m.pasteString(g -> {
          if (g==null) return;
          um.pushL("paste code");
          pasteText(v.room().asCodeblock(g));
          um.pop();
        });
        return true;
      case "deleteMsg":
        if (editing!=null) {
          ChatEvent toDel = editing;
          setEdit(null);
          v.room().delete(toDel);
          removeAll(); um.clear();
        }
        return true;
    }
    return super.keyF2(key, scancode, a);
  }
  
  public void typed(int codepoint) {
    if (m.chatTyped(codepoint)) return;
    super.typed(codepoint);
  }
  
  TextCompletionPopup psP;
  String prevTag;
  public void tickC() {
    super.tickC();
    // TODO move to some "on modified" method (need some "on cursor moved" too?)
    doCompletion(true);
  }
  public void doCompletion(boolean visible) {
    String tag = null;
    int selY = -1;
    int selSX = -1;
    int selEX = -1;
    Vec<Pair<String, String>> entries = Vec.of(); // list of {displayed, inserted}
    findComplete: if (m.view!=null && visible && (m.focusedVW==(psP==null? null : psP.vw) || m.focusNode()==this) && cs.sz==1 && cs.get(0).sx!=0 && cs.get(0).reg() && gc.getProp("chat.userAutocomplete").b()) {
      Cursor c = cs.get(0);
      Line line = lns.get(c.sy);
      
      // user completion
      if (c.sx>=line.sz() || line.get(c.sx)==' ') {
        int sx = c.sx;
        while (sx>0 && line.get(sx-1)!=' ') sx--;
        if (sx+1<c.sx && line.get(sx)=='@') {
          v.room().retryOnFullUserList(() -> {
            prevTag = null;
            doCompletion(true);
          });
          sx++;
          String text = new String(line.get(sx, c.sx));
          for (Chatroom.UserRes u : m.view.room().autocompleteUsers(text)) entries.add(new Pair<>(u.disp, u.src));
          if (entries.sz > 0) {
            tag = "user;"+text;
            selY = c.sy;
            selSX = sx-1;
            selEX = c.sx;
            break findComplete;
          }
        }
      }
      
      if (c.sy==0 && c.sx>=1 && get(0,0,1,0).charAt(0)=='/' && v instanceof MxLiveView) {
        String curr = get(1, 0, c.sx, 0);
        Vec<MxChatroom.MxCommand> cmds = ((MxLiveView) v).r.commands.filter(cmd -> cmd.name.contains(curr));
        cmds.sort(Comparator.comparing(cmd -> cmd.name));
        if (cmds.sz==1 && cmds.get(0).name.equals(curr)) cmds.clear();
        for (MxChatroom.MxCommand cmd : cmds) entries.add(new Pair<>("/"+cmd.name, "/"+cmd.name+(cmd.hasArgs? " " : "")));
        if (entries.sz > 0) {
          tag = "cmd;"+curr;
          selY = 0;
          selSX = 0;
          selEX = c.sx;
          break findComplete;
        }
      }
    }
    
    
    if (!Objects.equals(prevTag, tag)) {
      if (psP!=null) { psP.close(); psP=null; }
      if (entries.sz > 0) {
        psP = new TextCompletionPopup(this, selSX, selEX, selY);
        
        NodeVW vw = psP.openVW();
        for (Pair<String, String> e : entries) psP.node.add(new MenuNode.MINode(psP.node.ctx, e.a, e.b)); 
        vw.newRect();
      }
      prevTag = tag;
    }
  }
  
  public int action(Key key, KeyAction a) {
    if (globalKey(key, a)) return 1;
    int r0 = super.action(key, a);
    if (r0==1) return r0;
    if (m.onCancel(key, a, () -> {
      if (editing !=null) { setEdit  (null);     return true; }
      if (replying!=null) { markReply(null);     return true; }
      if (getAll().length() != 0) { removeAll(); return true; }
      return false;
    })) return 1;
    return r0;
  }
  
  
  
  public void setEdit(ChatEvent m) {
    if (!markEdit(m)) return;
    removeAll();
    if (editing!=null) {
      append(editing.getSrc());
      um.clear();
    }
  }
  private boolean markEdit(ChatEvent m) {
    if (replying!=null) return false;
    if (editing!=null && editing.n!=null) editing.mark(0);
    editing = m;
    if (editing!=null && editing.n!=null) editing.mark(1);
    this.m.updActions();
    return true;
  }
  public void markReply(ChatEvent m) {
    if (editing!=null) return;
    if (replying!=null && replying.n!=null) replying.mark(0);
    replying = m;
    if (replying!=null && replying.n!=null) replying.mark(2);
    this.m.updActions();
  }
  
  public void send() {
    String s = getAll();
    if (s.isEmpty()) return;
    
    if (editing!=null) {
      if (getAll().equals(editing.getSrc())) return;
      v.edit(editing, s);
    } else {
      v.post(s, replying==null? null : replying.id);
    }
    
    markEdit(null);
    markReply(null);
    removeAll();
    um.clear();
  }
  
  public boolean globalKey(Key key, KeyAction a) {
    if (psP==null) return false;
    switch (gc.keymap(key, a, "chat.autocomplete")) {
      case "prev": ((MenuNode) psP.node).focusPrev(); return true;
      case "next": ((MenuNode) psP.node).focusNext(); return true;
      case "acceptOnly":
        if (psP.node.ch.sz==1) {
          ((MenuNode.MINode) psP.node.ch.get(0)).run();
          return true;
        }
        return isFocused();
    }
    return false;
  }
  
  public void roomHidden() {
    doCompletion(false);
  }
  public void roomShown() {
    if (replying!=null) markReply(replying);
    if (editing!=null) markEdit(editing);
  }
  
}
