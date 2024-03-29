package chat.mx;

import chat.*;
import chat.ui.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.*;
import dzaima.ui.node.types.editable.*;
import dzaima.utils.*;
import dzaima.utils.JSON.*;
import io.github.humbleui.skija.Image;
import libMx.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

public class MxChatroom extends Chatroom {
  public static final int DEFAULT_MSGS = 50;
  
  public final MxChatUser u;
  public final MxRoom r;
  public String canonicalAlias, description;
  public String[] altAliases = new String[0];
  private int nameState = 0; // 0 - none; 1 - user; 2 - alias; 3 - primary
  
  public boolean msgLogToStart = false;
  public String prevBatch;
  public MxEvent lastEvent;
  public final MxLog log;
  
  public enum UserStatus { LEFT, INVITED, JOINED, BANNED, KNOCKING }
  public static class UserData { public String username, avatar; UserStatus s = UserStatus.LEFT; }
  public final HashMap<String, UserData> userData = new HashMap<>();
  
  public final PowerLevelManager powerLevels = new PowerLevelManager();
  
  public final HashMap<String, String> latestReceipts = new HashMap<>(); // map from user ID to ID of last message the user has a read receipt on
  private static class EventInfo { String closestVisible; int monotonicID; }
  private final HashMap<String, EventInfo> eventInfo = new HashMap<>(); // map from any event ID to last visible message in the log before this
  private String lastVisible;
  
  public SpaceInfo spaceInfo;
  public enum MyStatus { INVITED, JOINED, LEFT, FORGOTTEN }
  public MyStatus myStatus;
  
  public MxChatroom(MxChatUser u, String rid, Obj init, MyStatus status0) {
    super(u);
    this.myStatus = status0;
    this.log = new MxLog(this);
    this.u = u;
    this.r = u.s.room(rid);
    update(status0, init);
    if (status0!=MyStatus.INVITED) initPrevBatch(init);
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
    if (status0!=MyStatus.INVITED) { ping = false; unread = 0; unreadChanged(); }
    
    
    commands.put("md", left -> new MxFmt(left, MDParser.toHTML(left, this::onlyDisplayname)));
    
    Function<String,MxFmt> text = left -> new MxFmt(left, Utils.toHTML(left, true));
    commands.put("text", text);
    commands.put("plain", text);
    
    commands.put("html", left -> new MxFmt(left, left));
    commands.put("me", left -> {
      MxFmt f = parse(left);
      f.type = MxFmt.Type.EMOTE;
      return f;
    });
    commands.put("goto", left -> {
      u.openLink(left, Extras.LinkType.UNK, null);
      return null;
    });
    commands.put("sort", left -> {
      MxLog l = null;
      if (m.view instanceof MxChatroom) l = ((MxChatroom)m.view).log;
      else if (m.view instanceof MxTranscriptView) l = ((MxTranscriptView)m.view).log;
      if (l!=null) {
        l.list.sort(Comparator.comparing(k -> k.time));
        m.toView(m.view);
      }
      return null;
    });
    commands.put("theme", left -> {
      switch (left) {
        case "light": m.setTheme(ChatMain.Theme.light); break;
        case "dark": m.setTheme(ChatMain.Theme.dark); break;
      }
      return null;
    });
    commands.put("roomnick", left -> {
      u.queueNetwork(() -> u.u.setRoomNick(r, left));
      return null;
    });
    commands.put("globalnick", left -> {
      u.queueNetwork(() -> u.u.setGlobalNick(left));
      return null;
    });
  }
  public void initPrevBatch(Obj init) {
    Obj timeline = init.obj("timeline", Obj.E);
    prevBatch = !timeline.bool("limited", true)? null : timeline.str("prev_batch", null);
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
      case "m.room.create":
        String type = ct.str("type", "");
        if (type.equals("m.space")) spaceInfo = new SpaceInfo(this);
        break;
      case "m.space.child":
        if (spaceInfo!=null && ev.hasStr("state_key")) {
          spaceInfo.childInfo(ev.str("state_key"), ct.size()!=0);
        }
        break;
      case "m.room.topic":
        description = ct.str("topic", null);
        break;
      case "m.room.canonical_alias":
        altAliases = ct.arr("alt_aliases", Arr.E).strArr();
        String alias = ct.str("alias", null);
        if (alias!=null) {
          if (nameState<=2) {
            setName(alias);
            nameState = 2;
          }
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
  public void update(MyStatus ns, Obj sync) {
    MyStatus ps = myStatus;
    myStatus = ns;
    boolean pInv = ps==MyStatus.INVITED;
    boolean nInv = ns==MyStatus.INVITED;
    if (nInv) pinged();
    boolean inviteToJoin = pInv && ns==MyStatus.JOINED;
    if (inviteToJoin) {
      log.completelyClear();
      initPrevBatch(sync);
    }
    
    Arr stateList = sync.obj(nInv?"invite_state":"state", Obj.E).arr("events",Arr.E);
    for (Obj ev : stateList.objs()) {
      anyEvent(ev, ev.obj("content"));
    }
    Arr eventList = nInv? stateList : sync.obj("timeline").arr("events");
    
    HashSet<String> seen = new HashSet<>();
    for (Obj ev : eventList.objs()) {
      if (!seen.add(ev.str("event_id", "(unknown)")) && !nInv) {
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
      anyEvent(ev, ct);
      switch (ev.str("type")) {
        case "m.room.redaction":
          String e = ev.str("redacts", "");
          MxChatEvent m = log.get(e);
          if (m!=null) {
            m.delete(ev);
          }
          break;
      }
    }
    
    for (Obj ev : sync.obj("ephemeral",Obj.E).arr("events",Arr.E).objs()) {
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
    
    if ((pInv || nInv) && m.view==this) m.toRoom(this); // refresh "input" field
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
  public Pair<Boolean,Integer> highlight(String s) {
    String[] cmd = command(s);
    boolean md = m.gc.getProp("chat.markdown").b();
    if (cmd.length == 1) return new Pair<>(md, 0);
    String c0 = cmd[0];
    if (c0.equals("me")) return new Pair<>(md, 0);
    md = c0.equals("md");
    return new Pair<>(md, commands.get(c0)!=null? c0.length()+1 : 0);
  }
  
  public final HashMap<String, Function<String,MxFmt>> commands = new HashMap<>();
  public void post(String s, String target) {
    MxFmt f;
    String[] cmd = command(s);
    getF: {
      if (cmd.length == 2) {
        Function<String, MxFmt> fn = commands.get(cmd[0]);
        if (fn != null) {
          f = fn.apply(cmd[1]);
          if (f == null) return;
          break getF;
        }
      }
      f = parse(s);
    }
    
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
      protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a) || ChatMain.keyFocus(pw, key, a) || true; }
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
            Image img = Image.makeDeferredFromEncodedBytes(data);
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
      if (v.s==UserStatus.JOINED || v.s==UserStatus.INVITED || v.s==UserStatus.KNOCKING) {
        String src = k.substring(1).toLowerCase();
        String username = v.username;
        String disp = username==null? src : username.toLowerCase();
        if (src.startsWith(term) || (username!=null && disp.startsWith(term))) {
          if (src.equals(term)) complete[0] = true;
          res.add(new UserRes(disp, k));
        }
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
      if (m.gc.getProp("chat.notifyOnEdit").b()) changeUnread(1, false);
      else if (unread==0) readAll();
    } else if (cm.important()) {
      changeUnread(1, false);
    }
    unreadChanged();
    return cm;
  }
  
  public void show() { log.show(); super.show(); }
  public void hide() { super.hide(); log.hide(); }
  
  
  public void pinged() {
    changeUnread(0, true);
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
  
  public Node inputPlaceContent() {
    if (myStatus==MyStatus.INVITED) {
      Node n = m.ctx.make(m.gc.getProp("chat.inviteOptions").gr());
      ((BtnNode) n.ctx.id("accept")).setFn(b -> r.selfJoin());
      ((BtnNode) n.ctx.id("deny")).setFn(b -> r.selfRejectInvite());
      return n;
    }
    return input;
  }
  
  public void muteStateChanged() {
    u.saveRooms();
  }
  
  public void tick() {
    super.tick();
    if (olderRes!=null) {
      if (olderRes.events.isEmpty()) msgLogToStart = true;
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
    ViewRoom.viewRooms(this);
  }
  public void viewUsers() {
    ViewUsers.viewUsers(this);
  }
  public int memberCount() {
    return new Vec<>(new ArrayList<>(userData.values())).filter(c -> c.s==UserStatus.JOINED).sz;
  }
  
  
  public void confirmLeave(PartialMenu pm, String path, String id, Runnable run) {
    pm.add(m.gc.getProp(path).gr(), id, () -> {
      new Popup(m) {
        protected Rect fullRect() { return centered(m.ctx.vw, 0, 0); }
        protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a) || ChatMain.keyFocus(pw, key, a) || true; }
        protected void unfocused() { close(); }
        protected void setup() { }
        protected void preSetup() {
          node.ctx.id("msg").add(new StringNode(m.ctx, m.gc.getProp(path+"Msg").str()));
          node.ctx.id("msg2").add(new StringNode(m.ctx, m.gc.getProp(path+"Msg2").str()));
          node.ctx.id("run").add(new StringNode(m.ctx, m.gc.getProp(path+"Btn").str()));
          node.ctx.id("room").add(new StringNode(m.ctx, title()));
          ((BtnNode) node.ctx.id("cancel")).setFn(b -> close());
          ((BtnNode) node.ctx.id("run")).setFn(b -> { run.run(); close(); });
        }
      }.openVW(m.gc, m.ctx, m.gc.getProp("chat.mx.roomMenu.confirmLeave").gr(), true);
    });
  }
  public void roomMenu(Click c, int x, int y, Runnable onClose) {
    PartialMenu pm = new PartialMenu(m.gc);
    muteState.addMenuOptions(pm);
    pm.addSep();
    confirmLeave(pm, "chat.mx.roomMenu.leave", "leave", r::selfLeave);
    // confirmLeave(pm, "chat.mx.roomMenu.forget", "forget", () -> { r.selfLeave(); r.selfForget(); });
    pm.add(pm.gc.getProp("chat.mx.roomMenu.room").gr(), s -> {
      switch (s) {
        case "copyLink": actionCopyLink(); return true;
        case "copyID": actionCopyID(); return true;
        case "wrap": RoomListNode.DirStartNode.wrap(u, node); return true;
        default: return false;
      }
    });
    pm.open(node.ctx, c, onClose);
  }
  
  public String prettyID() {
    return canonicalAlias==null? r.rid : canonicalAlias;
  }
  private void actionCopyLink() { m.copyString(r.link()); }
  private void actionCopyID() { m.copyString(prettyID()); }
  
  
  public static class SpaceInfo extends RoomListNode.ExternalDirInfo {
    public final MxChatroom r;
    public String customName;
    public SpaceInfo(MxChatroom r) { this.r = r; }
    
    public void setLocalName(String val) {
      customName = val;
      nameUpdated();
    }
    public void nameUpdated() {
      if (node!=null) node.setName(getName());
    }
    public String getName() {
      return customName!=null? customName : officialName();
    }
    public String officialName() {
      return r.name;
    }
    
    public void addToMenu(PartialMenu pm) {
      pm.add(pm.gc.getProp("chat.mx.roomMenu.space").gr(), (s) -> {
        switch (s) {
          case "localRename": node.startEdit(); return true;
          case "copyLink": r.actionCopyLink(); return true;
          case "copyID": r.actionCopyID(); return true;
          case "viewInternal": r.node.leftClick(); return true;
          default: return false;
        }
      });
    }
    public void nodeAttached() {
      nameUpdated();
    }
    
    HashSet<String> children = new HashSet<>();
    HashSet<String> children0 = new HashSet<>();
    public void childInfo(String id, boolean has) {
      if (has) { children.add(id);    children0.add(id); }
      else     { children.remove(id); children0.remove(id); }
    }
  }
  public RoomListNode.ExternalDirInfo asDir() {
    return spaceInfo;
  }
  public void setName(String name) {
    super.setName(name);
    if (spaceInfo!=null) spaceInfo.nameUpdated();
  }
  
  public void userMenu(Click c, int x, int y, String uid) {
    Popup.rightClickMenu(m.gc, m.ctx, "chat.profile.menu", cmd -> {
      switch (cmd) { default: ChatMain.warn("Unknown menu option "+cmd); break;
        case "(closed)": break;
        case "view": viewProfile(uid); break;
        case "copyID": m.ctx.win().copyString(uid); break;
        case "copyLink": m.copyString(MxFmt.userURL(uid)); break;
      }
    }).takeClick(c);
  }
  
  public void viewProfile(String uid) {
    ViewProfile.viewProfile(uid, this);
  }
  
  public View getSearch() {
    return new MxSearchView(m, this);
  }
}