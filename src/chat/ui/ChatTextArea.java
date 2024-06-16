package chat.ui;

import chat.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.MenuNode;
import dzaima.ui.node.types.editable.code.CodeAreaNode;
import dzaima.utils.*;

import java.util.Objects;

public class ChatTextArea extends CodeAreaNode {
  public final ChatMain m;
  public final Chatroom r;
  public ChatEvent editing;
  public ChatEvent replying;
  
  public ChatTextArea(Chatroom r, Props props) {
    super(r.m.ctx, props);
    this.r = r;
    this.m = r.m;
  }
  
  public boolean keyF2(Key key, int scancode, KeyAction a) {
    String name = gc.keymap(key, a, "chat");
    switch (name) {
      case "editUp": case "editDn":
        if (m.view instanceof Chatroom && (editing==null? getAll().isEmpty() : getAll().equals(editing.getSrc())&&um.us.sz==0)) {
          Chatroom room = (Chatroom) m.view;
          setEdit(name.equals("editUp")? room.prevMsg(editing, true) : room.nextMsg(editing, true));
          return true;
        }
        break;
      case "replyUp": case "replyDn":
        if (m.view instanceof Chatroom && editing==null) {
          Chatroom room = (Chatroom) m.view;
          markReply(name.equals("replyUp")? room.prevMsg(replying, false) : room.nextMsg(replying, false));
          return true;
        }
        break;
      case "pasteCode":
        m.pasteString(g -> {
          if (g==null) return;
          um.pushL("paste code");
          pasteText(r.asCodeblock(g));
          um.pop();
        });
        return true;
      case "deleteMsg":
        if (editing!=null) {
          ChatEvent toDel = editing;
          setEdit(null);
          r.delete(toDel);
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
  String selText;
  public void tickC() {
    super.tickC();
    // TODO move to some "on modified" method
    doCompletion(true);
  }
  public void doCompletion(boolean visible) {
    String newSelText = null;
    int selY = -1;
    int selSX = -1;
    int selEX = -1;
    if (m.view!=null && visible && (m.focusedVW==(psP==null? null : psP.vw) || m.focusNode()==this) && cs.sz==1 && cs.get(0).sx!=0 && cs.get(0).reg() && gc.getProp("chat.userAutocomplete").b()) {
      int y0 = cs.get(0).sy;
      int ei = cs.get(0).sx;
      int si = ei;
      ChrVec ln = lns.get(y0).a;
      char[] lnA = ln.arr;
      if (ei>=ln.sz || lnA[ei]==' ') {
        while (si>0 && lnA[si-1]!=' ') si--;
        if (si+1<ei && lnA[si]=='@') {
          si++;
          newSelText = new String(lnA, si, ei-si);
          selY = y0;
          selSX = si-1;
          selEX = ei;
        }
      }
    }
    
    
    if (!Objects.equals(selText, newSelText)) {
      if (psP!=null) { psP.close(); psP=null; }
      if (newSelText!=null) {
        Vec<Chatroom.UserRes> r = m.view.room().autocompleteUsers(newSelText);
        if (r.sz>0) {
          psP = new TextCompletionPopup(this, selSX, selEX, selY);
          
          NodeVW vw = psP.openVW();
          for (Chatroom.UserRes c : r) psP.node.add(new MenuNode.MINode(psP.node.ctx, c.disp, c.src));
          vw.newRect();
        }
      }
      selText = newSelText;
    }
  }
  
  public int action(Key key, KeyAction a) {
    if (m.chatKey(key, 0, a)) return 1;
    int r0 = super.action(key, a);
    if (r0==1) return r0;
    if (a.press && key.k_esc()) {
      if (editing !=null) { setEdit  (null);     return 1; }
      if (replying!=null) { markReply(null);     return 1; }
      if (getAll().length() != 0) { removeAll(); return 1; }
    }
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
    
    if (editing!=null) r.edit(editing, s);
    else r.post(s, replying==null? null : replying.id);
    
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
