package chat;

import dzaima.ui.gui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Prop;
import dzaima.ui.node.types.MenuNode;
import dzaima.ui.node.types.editable.code.CodeAreaNode;
import dzaima.utils.*;

import java.util.Objects;

public class ChatTextArea extends CodeAreaNode {
  public final ChatMain m;
  
  public ChatTextArea(ChatMain m, Ctx ctx, String[] ks, Prop[] vs) {
    super(ctx, ks, vs);
    this.m = m;
  }
  
  public boolean keyF2(Key key, int scancode, KeyAction a) {
    String name = gc.keymap(key, a, "chat");
    switch (name) {
      case "editUp": case "editDn":
        if (m.view instanceof Chatroom && (m.editing==null? getAll().length()==0 : getAll().equals(m.editing.getSrc())&&um.us.sz==0)) {
          Chatroom room = (Chatroom) m.view;
          m.setEdit(name.equals("editUp")? room.prevMsg(m.editing, true) : room.nextMsg(m.editing, true));
          return true;
        }
        break;
      case "replyUp": case "replyDn":
        if (m.view instanceof Chatroom && m.editing==null) {
          Chatroom room = (Chatroom) m.view;
          m.markReply(name.equals("replyUp")? room.prevMsg(m.replying, false) : room.nextMsg(m.replying, false));
          return true;
        }
        break;
      case "deleteMsg":
        Chatroom room = m.room();
        if (room!=null && m.editing!=null) {
          ChatEvent toDel = m.editing;
          m.setEdit(null);
          room.delete(toDel);
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
  
  String prevSearch;
  Popup psP;
  NodeVW psV;
  public void tickC() {
    super.tickC();
    
    
    // TODO move to some "on modified" method
    String newSearch = null;
    int si=-1, ei=-1;
    int y0=-1;
    if (m.view!=null && (m.focusedVW==psV || m.focusNode==this) && cs.sz==1 && cs.get(0).sx!=0 && cs.get(0).reg() && gc.getProp("chat.userAutocomplete").b()) {
      y0 = cs.get(0).sy;
      ei = cs.get(0).sx;
      si = ei;
      ChrVec ln = lns.get(y0).a;
      char[] lnA = ln.arr;
      if (ei>=ln.sz || lnA[ei]==' ') {
        while (si>0 && lnA[si-1]!=' ') si--;
        if (si+1<ei && lnA[si]=='@') {
          si++;
          newSearch = new String(lnA, si, ei-si);
        }
      }
    }
    
    int toRemoveY = y0;
    int toRemoveS = si-1;
    int toRemoveE = ei;
    if (!Objects.equals(prevSearch, newSearch)) {
      if (psP!=null) psP.close();
      if (newSearch!=null) {
        Vec<Chatroom.UserRes> r = m.view.room().autocompleteUsers(newSearch);
        if (r.sz>0) {
          psP = new Popup(m) {
            protected void setup() { ((MenuNode) node).obj = this; }
            protected void unfocused() { }
            protected XY pos(XY size, Rect bounds) { return ChatTextArea.this.p.relPos(null).add(0, -size.y-gc.em/3); }
            public void stopped() { psP=null; psV=null; }
            
            protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a); }
            
            public void menuItem(String id) {
              um.pushL("insert username");
              remove(toRemoveS, toRemoveY, toRemoveE, toRemoveY);
              insert(toRemoveS, toRemoveY, id);
              um.pop();
              close();
              ChatTextArea.this.focusMe();
            }
          };
          
          psV = psP.openVW(gc, ctx, gc.getProp("chat.userAutocompleteUI").gr(), false);
          for (Chatroom.UserRes c : r) psP.node.add(new MenuNode.MINode(psP.node.ctx, c.disp, c.src));
          psV.resizeCanvas();
        }
      }
      prevSearch = newSearch;
    }
  }
  
  public int action(Key key, KeyAction a) {
    if (m.chatKey(key, 0, a)) return 1;
    int r0 = super.action(key, a);
    if (r0==1) return r0;
    if (a.press && key.k_esc()) {
      if (m.editing !=null) { m.setEdit  (null);                return 1; }
      if (m.replying!=null) { m.markReply(null);                return 1; }
      if (getAll().length() != 0)                { removeAll(); return 1; }
    }
    return r0;
  }
}
