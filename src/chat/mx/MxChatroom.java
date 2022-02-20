package chat.mx;

import chat.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.types.editable.Cursor;
import dzaima.utils.JSON.*;
import dzaima.utils.*;
import libMx.*;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public class MxChatroom extends Chatroom {
  public static final int DEFAULT_MSGS = 50;
  
  public final MxChatUser u;
  public final MxRoom r;
  public String canonicalAlias;
  public String[] altAliases = new String[0];
  private int nameState = 0; // 0 - none; 1 - user; 2 - alias; 3 - primary
  
  public boolean msgLogToStart = false;
  public String prevBatch;
  public MxEvent lastEvent;
  public final HashMap<String, String> usernames = new HashMap<>();
  public final MxLog log;
  
  public MxChatroom(MxChatUser u, String rid, Obj init) {
    super(u);
    this.log = new MxLog(this);
    this.u = u;
    this.r = u.s.room(rid);
    update(init);
    prevBatch = init.obj("timeline").str("prev_batch");
    // System.out.println(init.toString(2));
    if (nameState==0) {
      ArrayList<String> parts = new ArrayList<>();
      usernames.forEach((id, nick) -> {
        if (!id.equals(u.u.uid)) parts.add(nick);
      });
      if (parts.size()>0) {
        StringBuilder n = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
          if (i!=0) n.append(", ");
          if (i>0 && i==parts.size()-1) n.append("and ");
          n.append(parts.get(i));
        }
        nameState = 1;
        setName(n.toString());
      }
    }
    ping=false; unread=0; unreadChanged();
  }
  public void anyEvent(Obj ev, Obj ct) {
    switch (ev.str("type")) {
      case "m.room.member":
        String dn = ct.str("displayname", null);
        String sender = ev.str("sender");
        if (!ct.str("membership").equals("join") && usernames.containsKey(sender)) dn = null;
        if (dn!=null) usernames.put(sender, dn);
        break;
      case "m.room.canonical_alias":
        if (nameState>2) break; nameState = 2;
        altAliases = ct.arr("alt_aliases", Arr.E).strArr();
        String alias = ct.str("alias", null);
        if (alias!=null) {
          setName(alias);
          this.canonicalAlias = alias;
        }
        break;
      case "m.room.name":
        nameState = 3;
        if (ct.hasStr("name")) setName(ct.str("name"));
        break;
    }
  }
  public void update(Obj sync) {
    for (Obj ev : sync.obj("state").arr("events").objs()) {
      Obj ct = ev.obj("content");
      switch (ev.str("type")) {
        default:
          anyEvent(ev, ct);
          break;
        case "m.room.history_visibility": case "m.room.power_levels": case "m.room.join_rules": case "m.room.create": case "m.room.guest_access": break;
      }
    }
    
    for (Obj ev : sync.obj("timeline").arr("events").objs()) {
      Obj ct = ev.obj("content", null);
      pushMsg(new MxEvent(r, ev));
      //noinspection SwitchStatementWithTooFewBranches
      switch (ev.str("type")) {
        default:
          anyEvent(ev, ct);
          break;
        case "m.room.redaction":
          String e = ev.str("redacts", "");
          MxChatEvent m = log.get(e);
          if (m!=null) {
            m.type = "deleted";
            m.updateBody(true);
          }
          break;
      }
    }
    
    for (Obj ev : sync.obj("ephemeral").arr("events").objs()) {
      Obj ct = ev.obj("content");
      //noinspection SwitchStatementWithTooFewBranches
      switch (ev.str("type", "")) {
        case "m.typing":
          Arr ids = ct.arr("user_ids");
          StringBuilder typing = new StringBuilder();
          int l = ids.size();
          for (int i = 0; i < l; i++) {
            if (i>0) typing.append(i==l-1? " and " : ", ");
            typing.append(getUsername(ids.str(i)));
          }
          if (l>0) typing.append(l>1? " are typing …" : " is typing …");
          this.typing = typing.toString();
          m.updActions();
          break;
      }
    }
  }
  
  
  
  public MxFmt parse(String s) {
    if (m.gc.getProp("chat.markdown").b()) return new MxFmt(s, MDParser.toHTML(s, usernames::get));
    else return new MxFmt(s, Utils.toHTML(s, true));
  }
  public void post(String s, String target) {
    MxSendMsg f;
    if (s.startsWith("/")) {
      int ss = 1;
      while (!MDParser.border(s, ss)) ss++;
      int se = ss;
      if (ss<s.length() && Character.isWhitespace(s.charAt(se))) se++;
      String left = s.substring(se);
      switch (s.substring(1, ss)) {
        // case "img": {
        //   int p2 = left.indexOf('\n');
        //   String l0, body;
        //   if (p2==-1) { l0=left; body=""; }
        //   else {
        //     l0 = left.substring(0, p2);
        //     body = left.substring(p2+1);
        //   }
        //   String[] parts = l0.split(" ");
        //   if (parts.length!=4) {
        //     String last = parts[parts.length-1];
        //     parts = new String[4];
        //     parts[0] = parts[1] = parts[2] = "-1";
        //     parts[3] = last;
        //   }
        //   int[] is = new int[3];
        //   for (int i = 0; i < 3; i++) try { is[i] = Integer.parseInt(parts[i]); } catch (Throwable t) { is[i] = -1; }
        //   f = MxSendMsg.image(parts[3], body, is[0], is[1], is[2]);
        //   System.out.println(f.msgJSON());
        //   break;
        // }
        case "md":
          f = new MxFmt(left, MDParser.toHTML(left, usernames::get));
          break;
        case "text": case "plain":
          f = new MxFmt(left, Utils.toHTML(left, true));
          break;
        case "me":
          MxFmt f2 = parse(left);
          f2.type = MxFmt.Type.EMOTE;
          f = f2;
          break;
        default:
          f = parse(s);
          break;
      }
    } else f = parse(s);
    if (target!=null && f instanceof MxFmt) ((MxFmt) f).reply(r, target);
    u.queueNetwork(() -> r.s.primaryLogin.sendMessage(r, f));
  }
  public void edit(ChatEvent m, String s) {
    MxFmt f = parse(s);
    u.queueNetwork(() -> r.s.primaryLogin.editMessage(r, m.id, f));
  }
  public void delete(ChatEvent m) {
    u.queueNetwork(() -> r.s.primaryLogin.deleteMessage(r, m.id));
  }
  
  public void upload(Path p) {
    try {
      byte[] data = Files.readAllBytes(p);
      String name = p.getFileName().toString();
      int dot = name.lastIndexOf('.');
      String req = r.s.url+"/_matrix/media/r0/upload?access_token="+r.s.gToken;
      filename: if (dot!=-1) {
        for (int i = dot+1; i < name.length(); i++) {
          char c = name.charAt(i);
          if (!(c>='0'&c<='9' || c>='a'&c<='z' || c>='A'&c<='Z')) break filename;
        }
        req+= "&filename=file."+name.substring(dot+1);
      }
      Obj o = JSON.parseObj(Utils.post(req, data));
      
      m.input.append(u.s.mxcToURL(o.str("content_uri")));
    
      // String info;
      // try {
      //   Image img = Image.makeFromEncoded(data);
      //   info = img.getWidth()+" "+img.getHeight();
      //   img.close();
      // } catch (Throwable t) {
      //   info = "-1 -1";
      // }
      // int n = input.um.pushIgnore();
      // input.removeAll();
      // input.append("/img "+data.length+" "+info+" "+o.str("content_uri")+"\n"+p.getFileName().toString());
      // input.um.popIgnore(n);
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }
  
  public ChatEvent find(String id) { return log.find(id); }
  
  public ChatEvent prevMsg(ChatEvent msg, boolean mine) {
    Vec<MxChatEvent> l = log.list;
    int i = l.indexOf((MxChatEvent) msg);
    if (i==-1) i = l.sz;
    while (--i>=0) if ((!mine || l.get(i).mine) && !l.get(i).type.equals("deleted")) return l.get(i);
    return msg;
  }
  
  public ChatEvent nextMsg(ChatEvent msg, boolean mine) {
    Vec<MxChatEvent> l = log.list;
    int i = l.indexOf((MxChatEvent) msg);
    if (i==-1) return null;
    while (++i<l.sz) if ((!mine || l.get(i).mine) && !l.get(i).type.equals("deleted")) return l.get(i);
    return null;
  }
  
  
  public void pushMsg(MxEvent e) {
    lastEvent = e;
    MxChatEvent cm = log.processMessage(e, log.size(), true);
    if (open && cm!=null) m.addMessage(cm, true);
    if (cm==null) {
      if (m.gc.getProp("chat.notifyOnEdit").b()) unread++;
      else if (unread==0) readAll();
    } else if (cm.important()) {
      unread++;
    }
    unreadChanged();
  }
  
  public void show() { super.show(); log.show(); }
  public void hide() { super.hide(); log.hide(); }
  
  
  public void pinged() {
    ping = true;
    unreadChanged();
  }
  
  public boolean key(Key key, int scancode, KeyAction a) {
    return false;
  }
  
  public boolean typed(int codepoint) {
    if (codepoint=='`' && m.input.anySel()) {
      m.input.um.pushL("backtick code");
      for (Cursor c : m.input.cs) {
        String s = m.input.getByCursor(c);
        if ((s.contains("\\") || s.contains("`")) && !(s.startsWith(" ") || s.endsWith(" "))) {
          int l = 1;
          int cl = 0;
          for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i)!='`') {
              if (cl>l) l=cl;
              cl=0;
            } else cl++;
          }
          String fence = Tools.repeat('`', Math.max(cl,l)+1);
          s = fence+" "+s+" "+fence;
        } else {
          s = "`"+s.replace("\\", "\\\\").replace("`", "\\`")+"`";
        }
        c.clearSel();
        m.input.insert(c.sx, c.sy, s);
      }
      m.input.um.pop();
      return true;
    }
    return false;
  }
  
  public ChatUser user() { return u; }
  
  public volatile MxRoom.Chunk olderRes; // TODO move to queueRequest? (or atomic at least idk)
  public long nextOlder;
  public void older() {
    if (msgLogToStart) return;
    if (System.currentTimeMillis()<nextOlder) return;
    nextOlder = Long.MAX_VALUE;
    u.queueNetwork(() -> {
      MxRoom.Chunk r = this.r.beforeTok(prevBatch, log.size()<50? 50 : 100);
      if (r==null) { ChatMain.warn("MxRoom::before failed on token "+prevBatch); return; }
      olderRes = r;
    });
  }
  
  public void readAll() {
    MxEvent last = lastEvent;
    if (last==null) return;
    if (!last.uid.equals(u.id())) {
      u.queueNetwork(() -> r.readTo(last.id));
    }
  }
  
  public void tick() {
    if (olderRes!=null) {
      if (olderRes.events.size()==0) msgLogToStart = true;
      prevBatch = olderRes.eTok;
      log.addEvents(olderRes.events, false);
      olderRes = null;
      nextOlder = System.currentTimeMillis()+500;
    }
  }
  
  public String getUsername(String uid) {
    String s = usernames.get(uid);
    if (s==null) return uid.split(":")[0].substring(1);
    return s;
  }
  
  public String toString() { return name; }
  
  
  public String pill(MxEvent m, String id, String username) {
    boolean mine = u.u.uid.equals(m.uid);
    return "<pill mine=\""+mine+"\" id=\""+id+"\">"+username+"</pill>";
  }
  
  public static final Counter changeWindowCounter = new Counter();
  public void openTranscript(String msgId, Consumer<Boolean> callback) {
    m.currentAction = "loading message context...";
    m.updInfo();
    u.queueRequest(changeWindowCounter, () -> r.msgContext(msgId, 100), c -> {
      m.currentAction = null;
      m.updInfo();
      if (c!=null) toTranscript(msgId, c);
      callback.accept(c!=null);
    });
  }
  public void toTranscript(String highlightID, MxRoom.Chunk c) {
    m.toTranscript(new MxTranscriptView(this, highlightID, c));
  }
}