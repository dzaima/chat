package chat.mx;

import chat.*;
import dzaima.ui.eval.PNodeGroup;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.BtnNode;
import dzaima.ui.node.types.editable.*;
import dzaima.utils.*;
import dzaima.utils.JSON.*;
import io.github.humbleui.skija.Image;
import libMx.*;
import org.jsoup.nodes.Element;

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
  public final HashMap<String, String> usernames = new HashMap<>();
  public final MxLog log;
  
  private final HashMap<String, String> latestReceipts = new HashMap<>(); // map from user ID to ID of last message the user has a read receipt on
  private static class EventInfo { String closestVisible; int monotonicID; }
  private final HashMap<String, EventInfo> eventInfo = new HashMap<>(); // map from any event ID to last visible message in the log before this
  private String lastVisible;
  
  public MxChatroom(MxChatUser u, String rid, Obj init) {
    super(u);
    this.log = new MxLog(this);
    this.u = u;
    this.r = u.s.room(rid);
    update(init);
    prevBatch = init.obj("timeline").str("prev_batch");
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
  
  private int monotonicCounter = 0;
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
          if (l>0) typing.append(l>1? " are typing ???" : " is typing ???");
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
    Log.fine("mx receipt", uid+": "+prevID+(pm==null? " (not in log)" : "")+" ??? "+mid+" / "+(ei==null? "unknown" : visID)+(nm==null? " (not in log)" : ""));
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
    if (m.gc.getProp("chat.markdown").b()) return new MxFmt(s, MDParser.toHTML(s, usernames::get));
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
          f = new MxFmt(left, MDParser.toHTML(left, usernames::get));
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
      protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a); }
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
    usernames.forEach((k, v) -> {
      String src = k.substring(1).toLowerCase();
      String disp = v.toLowerCase();
      if (src.startsWith(term) || disp.startsWith(term)) {
        if (src.equals(term)) complete[0] = true;
        res.add(new UserRes(disp, k));
      }
    });
    if (complete[0]) res.clear();
    return res;
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
        input.insert(c.sx, c.sy, s);
      }
      input.um.pop();
      return true;
    }
    return false;
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
    String s = usernames.get(uid);
    if (s==null) return uid.split(":")[0].substring(1);
    return s;
  }
  
  public URLRes parseURL(String src) {
    int safety = m.imageSafety();
    if (src.startsWith("mxc://")) {
      return new URLRes(r.s.mxcToURL(src), safety>0);
    }
    return new URLRes(src, safety>1);
  }
  public void loadImg(Element e, String url, Consumer<Node> loaded) {
    u.queueRequest(null,
      () -> HTMLParser.image(this, url, MxChatUser.get("Load image", url), e.hasAttr("data-mx-emoticon")),
      loaded);
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
  
  
  protected void rightClick(Click c, int x, int y) {
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
}