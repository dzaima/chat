package chat.mx;

import chat.*;
import dzaima.ui.eval.PNodeGroup;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.types.BtnNode;
import dzaima.ui.node.types.editable.*;
import dzaima.utils.*;
import dzaima.utils.JSON.*;
import io.github.humbleui.skija.Image;
import libMx.*;

import java.io.IOException;
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
  public final MxLog log;
  
  public enum UserStatus { LEFT, INVITED, JOINED, BANNED, KNOCKING; }
  public static class UserData { public String username, avatar; UserStatus s = UserStatus.LEFT; }
  public final HashMap<String, UserData> userData = new HashMap<>();
  
  public final PowerLevelManager powerLevels = new PowerLevelManager();
  
  public final HashMap<String, String> latestReceipts = new HashMap<>(); // map from user ID to ID of last message the user has a read receipt on
  private static class EventInfo { String closestVisible; int monotonicID; }
  private final HashMap<String, EventInfo> eventInfo = new HashMap<>(); // map from any event ID to last visible message in the log before this
  private String lastVisible;
  
  public MxChatroom(MxChatUser u, String rid, Obj init) {
    super(u);
    this.log = new MxLog(this);
    this.u = u;
    this.r = u.s.room(rid);
    update(init);
    Obj timeline = init.obj("timeline", Obj.E);
    prevBatch = !timeline.bool("limited", true)? null : timeline.str("prev_batch", null);
    if (nameState==0) {
      ArrayList<String> parts = new ArrayList<>();
      userData.forEach((id, d) -> {
        if (!id.equals(u.u.uid)) parts.add(d.username==null? id : d.username);
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
        UserData d = this.userData.computeIfAbsent(ev.str(ev.hasStr("state_key")? "state_key" : "sender"), (s) -> new UserData());
        String m = ct.str("membership", "");
        if (m.equals("join")) {
          d.username = ct.str("displayname", null);
          d.avatar = ct.str("avatar_url", null);
        }
        switch (m) {
          case "invite": d.s = UserStatus.INVITED; break;
          case "join": d.s = UserStatus.JOINED; break;
          case "leave": d.s = UserStatus.LEFT; break;
          case "ban": d.s = UserStatus.BANNED; break;
          case "knock": d.s = UserStatus.KNOCKING; break;
        }
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
      case "m.room.power_levels":
        powerLevels.update(ct);
        break;
    }
  }
  
  private int monotonicCounter = 0;
  public void update(Obj sync) {
    for (Obj ev : sync.obj("state").arr("events").objs()) {
      Obj ct = ev.obj("content");
      switch (ev.str("type")) {
        default:
          anyEvent(ev, ct);
          break;
        case "m.room.history_visibility": case "m.room.join_rules": case "m.room.create": case "m.room.guest_access": break;
      }
    }
    
    HashSet<String> seen = new HashSet<>();
    for (Obj ev : sync.obj("timeline").arr("events").objs()) {
      if (!seen.add(ev.str("event_id", "(unknown)"))) {
        Log.info("skipping duplicate event with ID "+ev.str("event_id", "(unknown)")); // Synapse duplicates join event :|
        continue;
      }
      Obj ct = ev.obj("content", null);
      MxEvent mxEv = new MxEvent(r, ev);
      MxChatEvent newObj = pushMsg(mxEv);
      if (newObj!=null) lastVisible = mxEv.id;
      EventInfo ei = new EventInfo();
      ei.closestVisible = lastVisible;
      ei.monotonicID = monotonicCounter++;
      if (newObj!=null) newObj.monotonicID = ei.monotonicID;
      eventInfo.put(mxEv.id, ei);
      if (ev.hasStr("sender")) setReceipt(ev.str("sender"), mxEv.id);
      //noinspection SwitchStatementWithTooFewBranches
      switch (ev.str("type")) {
        default:
          anyEvent(ev, ct);
          break;
        case "m.room.redaction":
          String e = ev.str("redacts", "");
          MxChatEvent m = log.get(e);
          if (m!=null) {
            m.delete(ev);
          }
          break;
      }
    }
    
    for (Obj ev : sync.obj("ephemeral").arr("events").objs()) {
      Obj ct = ev.obj("content", Obj.E);
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
        case "m.receipt":
          for (Entry msg : ct.entries()) {
            String newID = msg.k;
            for (Entry user : msg.v.obj().obj("m.read", Obj.E).entries()) {
              setReceipt(user.k, newID);
            }
          }
          break;
      }
    }
  }
  
  public void setReceipt(String uid, String mid) {
    EventInfo ei = eventInfo.get(mid);
    String visID = ei==null? mid : ei.closestVisible;
    
    String prevID = latestReceipts.get(uid);
    MxChatEvent pm = prevID==null? null : find(prevID);
    MxChatEvent nm = find(visID);
    Log.fine("mx receipt", uid+": "+prevID+(pm==null? " (not in log)" : "")+" → "+mid+" / "+(ei==null? "unknown" : visID)+(nm==null? " (not in log)" : ""));
    if (ei!=null && pm!=null && pm.monotonicID>ei.monotonicID) {
      Log.fine("mx receipt", "cancelling read receipt update due to non-monotonic");
      return;
    }
    latestReceipts.put(uid, visID);
    
    if (pm!=null) {
      HashSet<String> rs = pm.receipts;
      if (rs!=null) {
        rs.remove(uid);
        if (rs.size()==0) pm.receipts = null;
      }
      m.updateExtra(pm);
    }
    
    if (nm!=null) {
      HashSet<String> rs = nm.receipts;
      if (rs==null) rs = nm.receipts = new HashSet<>();
      rs.add(uid);
      m.updateExtra(nm);
    }
  }
  
  public MxFmt parse(String s) {
    if (m.gc.getProp("chat.markdown").b()) return new MxFmt(s, MDParser.toHTML(s, this::onlyDisplayname));
    else return new MxFmt(s, Utils.toHTML(s, true));
  }
  private String[] command(String s) {
    if (!s.startsWith("/")) return new String[]{s};
    int ss = 1;
    while (!MDParser.border(s, ss)) ss++;
    int se = ss;
    if (se<s.length() && Character.isWhitespace(s.charAt(se))) se++;
    return new String[]{s.substring(1, ss), s.substring(se)};
  }
  public boolean highlight(String s) {
    String[] cmd = command(s);
    boolean def = m.gc.getProp("chat.markdown").b();
    if (cmd.length==1 || cmd[0].equals("me")) return def;
    return cmd[0].equals("md");
  }
  public void post(String s, String target) {
    MxFmt f;
    String[] cmd = command(s);
    if (cmd.length==2) {
      String left = cmd[1];
      switch (cmd[0]) {
        case "md":
          f = new MxFmt(left, MDParser.toHTML(left, this::onlyDisplayname));
          break;
        case "text": case "plain":
          f = new MxFmt(left, Utils.toHTML(left, true));
          break;
        case "html":
          f = new MxFmt(left, left);
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
    
    if (target!=null) {
      MxChatEvent tce = log.msgMap.get(target);
      if (tce!=null) f.reply(r, target, tce.e0.uid, tce.username);
      else f.reply(r, target);
    }
    u.queueNetwork(() -> r.s.primaryLogin.sendMessage(r, f));
  }
  public void edit(ChatEvent m, String s) {
    MxFmt f = parse(s);
    u.queueNetwork(() -> r.s.primaryLogin.editMessage(r, m.id, f));
  }
  public void delete(ChatEvent m) {
    u.queueNetwork(() -> r.s.primaryLogin.deleteMessage(r, m.id));
  }
  
  
  public void upload() {
    new Popup(m) {
      protected Rect fullRect() { return centered(m.ctx.vw, 0, 0); }
      protected void unfocused() { close(); }
      protected boolean key(Key key, KeyAction a) { defaultKeys(key, a); return true; } // don't return keyboard control back to chat text input
      EditNode name, mime, path;
      protected void setup() {
        name = (EditNode) node.ctx.id("name");
        mime = (EditNode) node.ctx.id("mime");
        path = (EditNode) node.ctx.id("path");
        ((BtnNode) node.ctx.id("choose")).setFn(c -> m.openFile(null, null, p -> {
          if (p==null) return;
          path.removeAll(); path.append(p.toString());
          name.removeAll(); name.append(p.getFileName().toString());
          String mimeType = null;
          try {
            mimeType = Files.probeContentType(p);
          } catch (IOException e) {
            Log.stacktrace("mx mime-type", e);
          }
          mime.removeAll();
          mime.append(mimeType!=null? mimeType : "application/octet-stream");
        }));
        ((BtnNode) node.ctx.id("getLink")).setFn(c -> {
          String l = getUpload();
          if (l==null) return;
          input.append(u.s.mxcToURL(l));
          close();
        });
        ((BtnNode) node.ctx.id("sendMessage")).setFn(c -> {
          String l = getUpload();
          if (l==null) return;
          int size = data.length;
          int w = -1, h = -1;
          try {
            Image img = Image.makeFromEncoded(data);
            w = img.getWidth();
            h = img.getHeight();
            img.close();
          } catch (Throwable e) {
            Log.stacktrace("mx get image info", e);
          }
          
          MxSendMsg f = MxSendMsg.image(l, name.getAll(), mime.getAll(), size, w, h);
          u.queueNetwork(() -> r.s.primaryLogin.sendMessage(r, f));
          close();
        });
      }
      byte[] data;
      String getUpload() {
        try {
          data = Files.readAllBytes(Paths.get(path.getAll()));
          return upload(data, name.getAll(), mime.getAll());
        } catch (IOException e) {
          Log.stacktrace("mx upload", e);
          return null;
        }
      }
    }.open(m.gc, m.ctx, m.gc.getProp("chat.mxUpload").gr());
  }
  
  public Vec<UserRes> autocompleteUsers(String prefix) {
    String term = prefix.toLowerCase();
    Vec<UserRes> res = new Vec<>();
    boolean[] complete = new boolean[1];
    userData.forEach((k, v) -> {
      String src = k.substring(1).toLowerCase();
      String username = v.username;
      String disp = username==null? src : username.toLowerCase();
      if (src.startsWith(term) || (username!=null && disp.startsWith(term))) {
        if (src.equals(term)) complete[0] = true;
        res.add(new UserRes(disp, k));
      }
    });
    if (complete[0]) res.clear();
    return res;
  }
  
  public void mentionUser(String id) {
    input.um.pushL("tag user");
    input.pasteText(id+" ");
    input.um.pop();
  }
  
  public String upload(byte[] data, String name, String mime) {
    String req = r.s.url+"/_matrix/media/r0/upload?filename="+Utils.toURI(name)+"&access_token="+r.s.gToken;
    String res = Utils.postPut("POST", req, data, mime);
    Obj o = JSON.parseObj(res);
    
    return o.str("content_uri");
  }
  
  public MxChatEvent find(String id) { return log.find(id); }
  
  public ChatEvent prevMsg(ChatEvent msg, boolean mine) {
    Vec<MxChatEvent> l = log.list;
    int i = l.indexOf((MxChatEvent) msg);
    if (i==-1) i = l.sz;
    while (--i>=0) if ((!mine || l.get(i).mine) && !l.get(i).isDeleted()) return l.get(i);
    return msg;
  }
  
  public ChatEvent nextMsg(ChatEvent msg, boolean mine) {
    Vec<MxChatEvent> l = log.list;
    int i = l.indexOf((MxChatEvent) msg);
    if (i==-1) return null;
    while (++i<l.sz) if ((!mine || l.get(i).mine) && !l.get(i).isDeleted()) return l.get(i);
    return null;
  }
  
  
  public MxChatEvent pushMsg(MxEvent e) { // returns the event object if it's visible on the timeline
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
    return cm;
  }
  
  public void show() { log.show(); super.show(); }
  public void hide() { super.hide(); log.hide(); }
  
  
  public void pinged() {
    ping = true;
    unreadChanged();
  }
  
  public boolean key(Key key, int scancode, KeyAction a) {
    return false;
  }
  
  public boolean typed(int codepoint) {
    if (codepoint=='`' && input.anySel()) {
      input.um.pushL("backtick code");
      for (Cursor c : input.cs) {
        String s = input.getByCursor(c);
        c.clearSel();
        input.insert(c.sx, c.sy, asCodeblock(s));
      }
      input.um.pop();
      return true;
    }
    return false;
  }
  
  public String asCodeblock(String s) {
    if (s.contains("\n")) {
      int l = 2;
      for (String c : Tools.split(s, '\n')) {
        int cl = 0;
        while (cl < c.length() && c.charAt(cl)=='`') cl++;
        l = Math.max(l, cl);
      }
      String fence = Tools.repeat('`', l+1);
      if (s.endsWith("\n")) s = s.substring(0, s.length()-1);
      return fence+"\n"+s+"\n"+fence;
    } else if ((s.contains("\\") || s.contains("`")) && !(s.startsWith(" ") || s.endsWith(" "))) {
      int l = 1;
      int cl = 0;
      for (int i = 0; i < s.length(); i++) {
        if (s.charAt(i)!='`') {
          l = Math.max(l, cl);
          cl=0;
        } else cl++;
      }
      l = Math.max(l, cl);
      String fence = Tools.repeat('`', l+1);
      return fence+" "+s+" "+fence;
    } else {
      return "`"+s.replace("\\", "\\\\").replace("`", "\\`")+"`";
    }
  }
  
  public ChatUser user() { return u; }
  
  public volatile MxRoom.Chunk olderRes; // TODO move to queueRequest? (or atomic at least idk)
  public long nextOlder;
  public void older() {
    if (msgLogToStart || prevBatch==null) return;
    if (System.currentTimeMillis()<nextOlder) return;
    nextOlder = Long.MAX_VALUE;
    Log.fine("mx", "Loading older messages in room");
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
    assert uid.startsWith("@");
    UserData d = userData.get(uid);
    if (d==null || d.username==null) return uid.split(":")[0].substring(1);
    return d.username;
  }
  public String onlyDisplayname(String uid) {
    UserData d = userData.get(uid);
    return d==null? null : d.username;
  }
  
  public URLRes parseURL(String src) {
    int safety = m.imageSafety();
    if (MxServer.isMxc(src)) {
      return new URLRes(r.s.mxcToURL(src), safety>0);
    }
    return new URLRes(src, safety>1);
  }
  
  public String toString() { return name; }
  
  
  public String pill(MxEvent m, String id, String username) {
    boolean mine = u.u.uid.equals(m.uid);
    return "<pill mine=\""+mine+"\" id=\""+id+"\">"+username+"</pill>";
  }
  
  public static final Counter changeWindowCounter = new Counter();
  public void openTranscript(String msgId, Consumer<Boolean> callback, boolean force) {
    if (!force) {
      MxChatEvent m = log.get(msgId);
      if (m!=null) {
        m.highlight(false);
        callback.accept(true);
        return;
      }
    }
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
  
  public void viewRoomInfo() {
    viewUsers();
  }
  public void viewUsers() {
    ViewUsers.viewUsers(this);
  }
  
  protected void roomMenu(Click c, int x, int y) {
    PNodeGroup gr = node.gc.getProp("chat.mx.roomMenu.main").gr().copy();
    node.openMenu(true);
    
    Popup.rightClickMenu(node.gc, node.ctx, gr, cmd -> {
      switch (cmd) { default: ChatMain.warn("Unknown menu option "+cmd); break;
        case "(closed)":
          node.openMenu(false);
          break;
        case "copyLink":
          m.copyString(r.link());
          break;
        case "copyID":
          m.copyString(canonicalAlias==null? r.rid : canonicalAlias);
          break;
      }
    }).takeClick(c);
  }
  
  public void userMenu(Click c, int x, int y, String uid) {
    Popup.rightClickMenu(m.gc, m.ctx, "chat.profile.menu", cmd -> {
      switch (cmd) {
        case "view": viewProfile(uid); break;
        case "copyID": m.ctx.win().copyString(uid); break;
        case "copyLink": m.copyString(MxFmt.userURL(uid)); break;
      }
    }).takeClick(c);
  }
  
  public void viewProfile(String uid) {
    ViewProfile.viewProfile(uid, this);
  }
}