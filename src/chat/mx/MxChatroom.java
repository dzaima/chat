package chat.mx;

import chat.*;
import chat.Command.*;
import chat.ui.RoomListNode;
import chat.utils.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.types.*;
import dzaima.utils.JSON.*;
import dzaima.utils.*;
import libMx.*;

import java.util.*;
import java.util.function.*;

public class MxChatroom extends Chatroom {
  public static final boolean DEBUG_EVENTS = false;
  
  public final MxChatUser u;
  public final MxRoom r;
  public String canonicalAlias, description;
  public String[] altAliases = new String[0];
  private int nameState = 0; // 0 - none; 1 - user; 2 - alias; 3 - primary
  
  public final HashMap<String, MxChatEvent> allKnownEvents = new HashMap<>(); // keys should be exactly value.id
  public final HashMap<String, String> editRoot = new HashMap<>(); // map edit-event-id → message-id, as a key of allKnownEvents
  public final HashMap<String, MxLog> liveLogs = new HashMap<>(); // key is thread ID, or null key for outside-of-threads
  public final MxLiveView mainLiveView;
  
  private Promise<HashMap<String, UserData>> fullUserList = null;
  private Vec<Obj> memberEventsToProcess = null; // held for the duration of fullUserList calculation
  public enum UserStatus { LEFT, INVITED, JOINED, BANNED, KNOCKING }
  public static class UserData { public String username, avatar; UserStatus s = UserStatus.LEFT; boolean questionable = true; }
  private int joinedCount = 0;
  public final HashMap<String, UserData> userData = new HashMap<>(); // using directly may be problematic due to lazy loading!
  
  public final PowerLevelManager powerLevels = new PowerLevelManager();
  
  public final PairHashSet<MxLog, MxChatEvent> unreads = new PairHashSet<>();
  public final PairHashSet<MxLog, MxChatEvent> pings = new PairHashSet<>();
  
  public final HashMap<String, MxLog.Reaction> reactions = new HashMap<>();
  private static class EventInfo { String closestVisible; int monotonicID; } // TODO thread?
  private final HashMap<String, EventInfo> eventInfo = new HashMap<>(); // map from any event ID to last visible message in the log before this
  private String lastVisible;
  
  public SpaceInfo spaceInfo;
  public enum MyStatus { INVITED, JOINED, LEFT, FORGOTTEN }
  public MyStatus myStatus;
  
  public MxChatroom(MxChatUser u, String rid, Obj init, MyStatus status0) {
    super(u);
    this.myStatus = status0;
    this.u = u;
    this.r = u.s.room(rid);
    m.dumpInitial.accept(rid, init);
    
    MxLog l0 = new MxLog(this, null, null);
    mainLiveView = l0.liveView();
    liveLogs.put(null, l0);
    if (!u.lazyLoadUsers) fullUserList = Promise.create(res -> res.set(userData));
    
    update(status0, init, false);
    joinedCount = Obj.path(init, Num.ZERO, "summary", "m.joined_member_count").asInt();
    
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
        setOfficialName(n.toString());
      }
    }
    
    
    commands.addAll(u.commands);
    commands.add(new SimplePlainCommand("sort", () -> {
      MxLog l = visibleLog();
      if (l!=null) {
        l.list.sort(Comparator.comparing(k -> k.time));
        m.toView(m.view);
      }
    }));
    
    Function<String,MxFmt> text = left -> new MxFmt(left, Utils.toHTML(left, true));
    commands.add(new MxFmtCommand("text", true, text));
    commands.add(new MxFmtCommand("plain", true, text));
    commands.add(new MxFmtCommand("md", true, left -> new MxFmt(left, MDParser.toHTML(left, this::onlyDisplayname))));
    commands.add(new MxFmtCommand("html", true, left -> new MxFmt(left, left)));
    commands.add(new MxFmtCommand("me", true, left -> {
      MxFmt f = parse(left);
      f.type = MxFmt.Type.EMOTE;
      return f;
    }));
    
    commands.add(new SimpleArgCommand("set-nick-room", left -> u.queueNetwork(() -> u.u.setRoomNick(r, left))));
    
    commands.add(new SimpleArgCommand("set-room-name", left -> u.queueNetwork(() -> r.setRoomName(left))));
    commands.add(new IdArgCommand("kick",   id -> u.queueNetwork(() -> r.kick  (id, null))));
    commands.add(new IdArgCommand("ban",    id -> u.queueNetwork(() -> r.ban   (id, null))));
    commands.add(new IdArgCommand("unban",  id -> u.queueNetwork(() -> r.unban (id      ))));
    commands.add(new IdArgCommand("invite", id -> u.queueNetwork(() -> r.invite(id, null))));
    commands.add(new IdArgCommand("view-user", id -> ViewProfile.viewProfile(id, this)));
  }
  public void initPrevBatch(Obj init) {
    Obj timeline = init.obj("timeline", Obj.E);
    mainLiveView.prevBatch = !timeline.bool("limited", true)? null : timeline.str("prev_batch", null);
  }
  private String processMemberEvent(Obj ev, boolean isNew, boolean questionable) { // returns ID of joined user
    Obj ct = ev.obj("content");
    String id = ev.str(ev.hasStr("state_key")? "state_key" : "sender");
    assert id.startsWith("@") : id;
    UserData d = userData.computeIfAbsent(id, (s) -> new UserData());
    
    if (questionable && !d.questionable) return id;
    d.questionable = questionable;
    
    String m = ct.str("membership", "");
    if (m.equals("join")) {
      d.username = ct.str("displayname", null);
      d.avatar = ct.str("avatar_url", null);
    }
    if (isNew && d.s == UserStatus.JOINED) joinedCount--;
    
    switch (m) {
      case "invite": d.s = UserStatus.INVITED; break;
      case "join": d.s = UserStatus.JOINED; if (isNew) joinedCount++; break;
      case "leave": d.s = UserStatus.LEFT; break;
      case "ban": d.s = UserStatus.BANNED; break;
      case "knock": d.s = UserStatus.KNOCKING; break;
    }
    return m.equals("join")? id : null;
  }
  public void anyEvent(Obj ev, boolean liveTimeline) {
    Obj ct = ev.obj("content");
    switch (ev.str("type")) {
      case "m.room.member":
        if (memberEventsToProcess!=null) {
          memberEventsToProcess.add(ev);
          return;
        } else {
          String uid = processMemberEvent(ev, true, false);
          if (liveTimeline && uid!=null && u.autoban.contains(uid)) {
            u.autobanMember(uid, this);
          }
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
            setOfficialName(alias);
            nameState = 2;
          }
          this.canonicalAlias = alias;
        }
        break;
      case "m.room.name":
        nameState = 3;
        if (ct.hasStr("name")) setOfficialName(ct.str("name"));
        break;
      case "m.room.power_levels":
        powerLevels.update(ct);
        break;
    }
  }
  
  private int monotonicCounter = 0;
  public void update(MyStatus ns, Obj sync, boolean live) {
    m.dumpAll.accept(r.rid, sync);
    MyStatus ps = myStatus;
    myStatus = ns;
    boolean pInv = ps==MyStatus.INVITED;
    boolean nInv = ns==MyStatus.INVITED;
    boolean inviteToJoin = pInv && ns==MyStatus.JOINED;
    if (inviteToJoin) {
      allKnownEvents.clear();
      reactions.clear();
      for (MxLog l : liveLogs.values()) l.completelyClear();
      initPrevBatch(sync);
    }
    
    Arr stateList = sync.obj(nInv?"invite_state":"state", Obj.E).arr("events",Arr.E);
    Arr eventList = nInv? stateList : sync.obj("timeline").arr("events");
    Arr ephemeralList = sync.obj("ephemeral",Obj.E).arr("events",Arr.E);
    Log.info("mx room-updates", "room "+prettyID()+" received "+stateList.size()+" states, "+eventList.size()+" events, "+ephemeralList.size()+" ephemerals");
    
    // state
    for (Obj ev : stateList.objs()) {
      anyEvent(ev, false);
    }
    
    // regular timeline events
    HashSet<String> seen = new HashSet<>();
    for (Obj ev : eventList.objs()) {
      if (ev.hasStr("event_id")) {
        String evID = ev.str("event_id");
        if (!seen.add(evID) && !nInv) {
          Log.info("mx", "skipping duplicate event with ID "+evID); // Synapse duplicates join event :|
          continue;
        }
        if (eventInfo.containsKey(evID)) {
          Log.info("mx", "skipping already-received event with ID "+evID);
          continue;
        }
      }
      
      int counterID = monotonicCounter++;
      MxEvent mxEv = new MxEvent(r, ev, "auto_id_"+counterID); // manual ID for invite state which doesn't have event_id
      
      // for new joins we want to pick up the displayname on them, while for re-joins we want to keep the previous one for renames to work
      boolean earlyAdd = mxEv.type.equals("m.room.member") && !userData.containsKey(mxEv.uid);
      if (earlyAdd) anyEvent(ev, live);
      
      MxChatEvent newObj = pushMsg(mxEv, live);
      if (newObj!=null) {
        lastVisible = mxEv.id;
        if (live && mxEv.m!=null && u.autoban.contains(mxEv.m.uid)) {
          u.autobanRemoveMessage(newObj);
        }
      }
      EventInfo ei = new EventInfo();
      ei.closestVisible = lastVisible;
      ei.monotonicID = counterID;
      if (newObj!=null) newObj.monotonicID = ei.monotonicID;
      eventInfo.put(mxEv.id, ei);
      if (ev.hasStr("sender")) for (MxLog l : allLogsOf(mxEv)) setReceipt(l, ev.str("sender"), mxEv.id);
      if (!earlyAdd) anyEvent(ev, live);
      switch (ev.str("type")) {
        case "m.room.redaction":
          String e = ev.str("redacts", "");
          MxChatEvent m = editRootEvent(e);
          if (m!=null) m.delete(ev);
          break;
      }
    }
    
    // ephemeral events
    for (Obj ev : ephemeralList.objs()) {
      Obj ct = ev.obj("content", Obj.E);
      switch (ev.str("type", "")) {
        case "m.typing":
          Vec<String> uids = Vec.ofIterable(ct.arr("user_ids").strs());
          Vec<Username> names = uids.map(c -> getUsername(c, true));
          renderTyping(names);
          break;
        case "m.receipt":
          for (Entry msg : ct.entries()) {
            String newID = msg.k;
            for (Entry user : msg.v.obj().obj("m.read", Obj.E).entries()) {
              String threadID = user.v.obj().str("thread_id", null);
              if ("main".equals(threadID)) threadID = null; // TODO better handle unthreaded read receipts?
              setReceipt(getThreadLog(threadID), user.k, newID);
            }
          }
          break;
      }
    }
    
    if (nInv && globalLog().list.sz>0) {
      addPing(globalLog(), globalLog().list.peek());
      unreadChanged();
    }
    if ((pInv || nInv) && m.view==mainView()) m.toRoom(mainView()); // refresh "input" field
  }
  
  
  
  public final Vec<Command> commands = new Vec<>();
  public static class MxFmtCommand extends Command {
    public final Function<String, MxFmt> process;
    public MxFmtCommand(String name, boolean hasArgs, Function<String, MxFmt> process) {
      super(name, hasArgs);
      this.process = process;
    }
    public Object run(String s) { return process.apply(s); }
  }
  public static class IdArgCommand extends SimpleTestCommand {
    public IdArgCommand(String name, Consumer<String> id) {
      super(name, left -> { id.accept(left.replace(" ", "")); return true; });
    }
  }
  
  public MxFmt parse(String s) {
    if (m.gc.getProp("chat.markdown").b()) return new MxFmt(s, MDParser.toHTML(s, this::onlyDisplayname));
    else return new MxFmt(s, Utils.toHTML(s, true));
  }
  
  public Pair<Boolean,Integer> highlight(String s) {
    String[] cmd = splitCommand(s);
    boolean md = m.gc.getProp("chat.markdown").b();
    if (cmd.length == 1) return new Pair<>(md, 0);
    String c0 = cmd[0];
    if (c0.equals("me")) return new Pair<>(md, 0);
    md = c0.equals("md");
    return new Pair<>(md, commandPrefix(cmd, commands));
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
  
  
  
  private void renderTyping(Vec<Username> allNames) {
    Vec<Username> known = allNames.filter(c -> c.full.isResolved());
    StringBuilder typing = new StringBuilder();
    int l = known.size();
    for (int i = 0; i < l; i++) {
      if (i>0) typing.append(i==l-1? " and " : ", ");
      Username c = known.get(i);
      typing.append(c.best());
    }
    if (l>0) typing.append(l>1? " are typing …" : " is typing …");
    this.typing = typing.toString();
    m.updActions();
    
    Vec<Username> unknown = allNames.filter(c -> !c.full.isResolved());
    if (unknown.sz!=0) Promise.all(ignored -> renderTyping(allNames), unknown.map(c -> c.full));
  }
  
  public void setReceipt(MxLog l, String uid, String mid) {
    EventInfo ei = eventInfo.get(mid);
    String visID = ei==null? mid : ei.closestVisible;
    
    String prevID = l.latestReceipts.get(uid);
    MxChatEvent pm = prevID==null? null : find(prevID);
    MxChatEvent nm = find(visID);
    
    Log.fine("mx receipt", () -> uid+" in "+prettyID()+"+"+l.prettyID()+": "+
      prevID + (pm==null? " (not in log)" : "") +
      " → " + mid +
      (!Objects.equals(visID, mid)? " → "+visID : "") + (nm==null? " (not in log)" : ""));
    
    if (pm!=null && nm==null) {
      Log.fine("mx receipt", "Cancelling read receipt update due to the target being unknown");
      return;
    }
    if (ei!=null && pm!=null && pm.monotonicID>ei.monotonicID) {
      Log.fine("mx receipt", "Cancelling read receipt update due to non-monotonic");
      return;
    }
    l.latestReceipts.put(uid, visID);
    
    l.messageReceipts.remove(prevID, uid);
    l.messageReceipts.add(visID, uid);
    
    if (pm!=null) pm.updateExtra();
    if (nm!=null) nm.updateExtra();
  }
  
  public void delete(ChatEvent m) {
    u.queueNetwork(() -> r.s.primaryLogin.deleteMessage(r, m.id));
  }
  
  public boolean hasFullUserList() {
    return fullUserList!=null && fullUserList.isResolved();
  }
  public int getJoinedMemberCount() { // probably correct-ish even without full user list loaded
    return joinedCount;
  }
  public void retryOnFullUserList(Runnable then) {
    if (hasFullUserList()) return;
    getFullUserList().then(userData -> then.run());
  }
  
  public void cfgUpdated() {
    mainLiveView.cfgUpdated(); // TODO thread
  }
  
  public Promise<HashMap<String, UserData>> getFullUserList() {
    if (fullUserList==null) fullUserList = Promise.create(res -> {
      Log.info("mx users", "Getting full user list of "+prettyID());
      assert memberEventsToProcess==null;
      String tk = u.currentSyncToken;
      Runnable processIntermediate = () -> {
        for (Obj c : memberEventsToProcess) processMemberEvent(c, true, false);
        Log.info("mx users", "Got full user list of "+prettyID());
        memberEventsToProcess = null;
      };
      memberEventsToProcess = new Vec<>();
      u.queueRequest(() -> r.getFullMemberState(tk), us -> {
        if (us==null) {
          Log.warn("mx users", "Couldn't get full user list");
          processIntermediate.run();
          fullUserList = null;
          return;
        }
        for (Obj c : us.objs()) processMemberEvent(c, false, false);
        processIntermediate.run();
        res.set(userData);
        joinedCount = Vec.ofCollection(userData.values()).filter(c -> c.s==MxChatroom.UserStatus.JOINED).sz;
      });
    });
    return fullUserList;
  }
  public void doubleUserList(BiConsumer<HashMap<String, UserData>, Boolean> b) { // true on lazy result; a non-lazy result will always be given, but lazy may be omitted
    if (!hasFullUserList()) b.accept(userData, true);
    getFullUserList().then(r -> b.accept(r, false));
  }
  
  public MxLog globalLog() { return liveLogs.get(null); }
  public MxChatEvent find(String id) { return allKnownEvents.get(id); }
  
  public UnreadInfo unreadInfo() {
    return new UnreadInfo(unreads.uniqueB(MxChatEvent.class), !pings.isEmpty());
  }
  
  public String editRootOf(String id) {
    return editRoot.getOrDefault(id, id);
  }
  public MxChatEvent editRootEvent(String id) {
    return allKnownEvents.get(editRootOf(id));
  }
  
  public MxLog getThreadLog(String threadID) {
    MxLog l = liveLogs.get(threadID);
    if (l!=null) return l;
    
    l = new MxLog(this, threadID, null);
    liveLogs.put(threadID, l);
    maybeThreadRoot(globalLog().get(threadID));
    return l;
  }
  public MxLog primaryLogOf(MxEvent e) {
    if (e.m == null) return globalLog();
    if (e.m.threadId!=null) return getThreadLog(e.m.threadId);
    if (e.m.isEditEvent()) {
      MxChatEvent prev = editRootEvent(e.m.editsId);
      if (prev!=null && prev.e0.m!=null && prev.e0.m.threadId!=null) return getThreadLog(prev.e0.m.threadId);
    }
    return globalLog();
  }
  public Vec<MxLog> allLogsOf(MxEvent e) { // doesn't include own thread if not created yet
    MxLog primaryLog = primaryLogOf(e);
    MxLog myThread = liveLogs.get(e.id);
    if (myThread!=null) return Vec.of(primaryLog, myThread);
    return Vec.of(primaryLog);
  }
  
  public void maybeThreadRoot(MxChatEvent c) {
    if (c==null) return;
    MxLog selfThread = liveLogs.get(c.id);
    if (selfThread!=null && selfThread.globalPaging && !selfThread.contains(c)) {
      selfThread.addCompleteMessages(false, Vec.of(c));
    }
  }
  
  public void addPing(MxLog l, MxChatEvent e) { // call unreadChanged afterward!
    if (e.senderID().equals(u.id())) return;
    if (l.lv!=null) l.lv.beforeUnreadChange();
    pings.add(l, e);
  }
  public void addUnread(MxLog l, MxChatEvent e) { // call unreadChanged afterward!
    if (e.senderID().equals(u.id())) return;
    if (l.lv!=null) l.lv.beforeUnreadChange();
    unreads.add(l, e);
  }
  
  public MxChatEvent pushMsg(MxEvent e, boolean ping) { // returns the event object if it's visible on the timeline
    MxChatEvent cm = primaryLogOf(e).pushEventAtEnd(e, ping);
    maybeThreadRoot(cm);
    if (ping) {
      if (cm!=null) {
        if (cm.increasesUnread()) for (MxLog c : allLogsOf(e)) addUnread(c, cm);
      } else if (e.m!=null && e.m.isEditEvent() && m.gc.getProp("chat.notifyOnEdit").b()) {
        MxChatEvent root = editRootEvent(e.m.editsId);
        if (root!=null) for (MxLog c : allLogsOf(root.e0)) addUnread(c, root);
      }
      unreadChanged();
    }
    return cm;
  }
  
  MxChatEvent processEvent(MxEvent e, boolean newAtEnd, boolean ping) { // returns message that would be shown, or null if it's not to be displayed
    MxChatEvent known = allKnownEvents.get(e.id); // for when thread-specific pagination loads the event before the main pagination does; TODO impact on transcript?
    if (known!=null) return known;
    if (e.type.equals("m.reaction")) {
      Obj o = Obj.objPath(e.ct, Obj.E, "m.relates_to");
      if (newAtEnd) {
        if (o.str("rel_type","").equals("m.annotation")) {
          String key = o.str("key", "");
          String r_id = o.str("event_id", "");
          MxChatEvent r_ce = editRootEvent(r_id);
          Log.fine("mx reaction", "Reaction "+key+" added to "+r_id);
          
          if (r_ce!=null) {
            r_ce.addReaction(key, 1);
            MxLog.Reaction obj = new MxLog.Reaction();
            obj.to = r_ce;
            obj.key = key;
            reactions.put(e.id, obj);
          } else Log.fine("mx reaction", "Reaction was for unknown message");
        } else if (o.size()!=0) {
          Log.warn("mx reaction", "Unknown content[\"m.relates_to\"].rel_type value");
        }
      }
      return makeDebugNotice(e, newAtEnd);
    } else if (e.type.equals("m.room.redaction")) {
      MxLog.Reaction re = reactions.get(e.o.str("redacts", ""));
      if (re!=null) {
        Log.fine("mx reaction", "Reaction "+re.key+" removed from "+re.to.id);
        reactions.remove(e.id);
        re.to.addReaction(re.key, -1);
      }
      return makeDebugNotice(e, newAtEnd);
    }
    
    if (e.m==null) {
      return new MxChatNotice(this, e, newAtEnd);
    } else {
      if (e.m.isEditEvent()) {
        String edits = editRootOf(e.m.editsId);
        editRoot.put(e.id, edits);
        MxChatEvent prev = editRootEvent(e.m.editsId);
        if (prev instanceof MxChatMessage) ((MxChatMessage) prev).edit(e, ping);
        else Log.fine("mx", e.id+" attempted to edit "+edits+", which is unknown; assuming out of log");
        return makeDebugNotice(e, newAtEnd);
      } else {
        return new MxChatMessage(e.m, e, this, newAtEnd, ping);
      }
    }
  }
  
  public MxChatNotice makeDebugNotice(MxEvent e, boolean newAtEnd) {
    if (DEBUG_EVENTS) return new MxChatNotice(this, e, newAtEnd);
    return null;
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
  
  public void loadQuestionableMemberState(MxRoom.Chunk r) {
    if (r==null) return;
    for (MxEvent e : r.states) if (e.type.equals("m.room.member")) loadQuestionableMemberState(e);
  }
  private void loadQuestionableMemberState(MxEvent e) {
    processMemberEvent(e.o, false, true);
  }
  
  public void muteStateChanged() {
    u.saveRooms();
  }
  
  public LiveView mainView() {
    return mainLiveView;
  }
  
  private final HashMap<String, Promise<String>> inProgressUserLoads = new HashMap<>();
  public Username getUsername(String uid, boolean requestForFuture) {
    assert uid.startsWith("@") : uid;
    UserData d = userData.get(uid);
    Promise<String> full = d==null? null : Promise.resolved(calcBestUsername(uid, d));
    if (full==null && requestForFuture) full = inProgressUserLoads.computeIfAbsent(uid, uid1 -> Promise.create(res -> {
      Log.fine("mx users", "retrieving full member state for "+uid+" in " + prettyID());
      u.queueRequest(() -> r.getMemberState(uid), e -> {
        inProgressUserLoads.remove(uid);
        if (e == null) {
          Log.warn("mx users", "Couldn't get member state of "+uid);
          userData.computeIfAbsent(uid, s -> new UserData());
        } else {
          loadQuestionableMemberState(e);
        }
        res.set(calcBestUsername(uid, userData.get(uid)));
      });
    }));
    
    return new Username(calcBestUsername(uid, d), full);
  }
  
  private static String calcBestUsername(String uid, UserData d) {
    return d==null || d.username==null? uid.split(":")[0].substring(1) : d.username;
  }
  
  public String onlyDisplayname(String uid) {
    UserData d = userData.get(uid);
    return d==null? null : d.username;
  }
  public boolean lazyHasMember(String uid) {
    UserData d = userData.get(uid);
    return d!=null && d.s==UserStatus.JOINED;
  }
  
  public String toString() { return officialName; }
  
  
  public String pill(MxEvent m, String id, String username) {
    boolean mine = u.u.uid.equals(m.uid);
    return "<pill mine=\""+mine+"\" id=\""+id+"\">"+username+"</pill>";
  }
  
  public static final Counter roomChangeCounter = new Counter();
  public void highlightMessage(String msgId0, Consumer<Boolean> found0, boolean force) {
    String msgId = editRootOf(msgId0);
    Consumer<Boolean> found = found0!=null? found0 : (b) -> {
      if (!b) Log.warn("mx", "Expected to find message "+msgId+", but didn't");
    };
    
    gotoDirect: if (!force) {
      MxChatEvent ev = editRootEvent(msgId);
      if (ev==null) break gotoDirect;
      
      // search first in current view, then main live view, then other views
      Vec<MxLog> logs = Vec.ofCollection(liveLogs.values());
      
      logs.remove(mainLiveView.log);
      logs.insert(0, mainLiveView.log);
      
      MxLog currLog = visibleLog();
      if (currLog!=null) {
        logs.filterInplace(c -> c!=currLog);
        logs.insert(0, currLog);
      }
      
      for (MxLog c : logs) {
        if (c.contains(ev)) {
          if (currLog == c) ev.highlight(false);
          else m.toRoom(c.liveView(), ev);
          
          found.accept(true);
          return;
        }
      }
    }
    
    Runnable done = m.doAction("loading message context...");
    int action = roomChangeCounter.next();
    u.queueRequest(() -> r.msgContext(currMemberFilter(), msgId, 100), c -> {
      loadQuestionableMemberState(c);
      done.run();
      if (roomChangeCounter.superseded(action)) return;
      if (c!=null) toTranscript(msgId, c);
      found.accept(c!=null);
    });
  }
  
  public Obj currMemberFilter() {
    return MxRoom.roomEventFilter(!hasFullUserList());
  }
  
  private void toTranscript(String highlightID, MxRoom.Chunk c) {
    MxTranscriptView v = new MxTranscriptView(this, c);
    m.toTranscript(v, v.log.get(highlightID));
  }
  
  public MxLog logOfView(View v) {
    MxLog l = null;
    if (v instanceof MxLiveView) l = ((MxLiveView) v).log;
    else if (v instanceof MxTranscriptView) l = ((MxTranscriptView) v).log;
    return l!=null && l.r==this? l : null;
  }
  
  public MxLog visibleLog() {
    View v = m.view;
    return logOfView(v);
  }
  
  public MxLiveView currLiveView() {
    LiveView v = m.liveView();
    return v instanceof MxLiveView && ((MxLiveView) v).r==this? (MxLiveView) v : null;
  }
  
  public void viewRoomInfo() {
    ViewRoomInfo.viewRoomInfo(this);
  }
  public void viewUsers() {
    ViewUsers.viewUsers(this);
  }
  
  
  
  public void addConfirmLeave(PartialMenu pm, String path, String id, Runnable run) {
    pm.add(m.gc.getProp(path).gr(), id, () -> new Popup(m) {
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
    }.openVW(m.gc, m.ctx, m.gc.getProp("chat.mx.roomMenu.confirmLeave").gr(), true));
  }
  
  public void addMenuItems(PartialMenu pm) {
    super.addMenuItems(pm);
    pm.addSep();
    pm.add(pm.gc.getProp("chat.roomMenu.info").gr(), "info", () -> ViewRoomInfo.viewRoomInfo(this));
    pm.add(pm.gc.getProp("chat.mx.roomMenu.room").gr(), s -> {
      switch (s) {
        case "copyLink": actionCopyLink(); return true;
        case "copyID": actionCopyID(); return true;
        default: return false;
      }
    });
    
    pm.addSep();
    // addConfirmLeave(pm, "chat.mx.roomMenu.forget", "forget", () -> { r.selfLeave(); r.selfForget(); });
    addConfirmLeave(pm, "chat.mx.roomMenu.leave", "leave", r::selfLeave);
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
      if (node!=null) node.setName(getTitle());
    }
    public String getTitle() {
      return customName!=null? customName : officialName();
    }
    public String officialName() {
      return r.officialName;
    }
    
    public void addToMenu(PartialMenu pm) {
      pm.add(pm.gc.getProp("chat.roomMenu.renameLocally").gr(), "localRename", () -> node.editor.startEdit());
      pm.addSep();
      pm.add(pm.gc.getProp("chat.roomMenu.info").gr(), "info", () -> ViewRoomInfo.viewRoomInfo(r));
      pm.add(pm.gc.getProp("chat.mx.roomMenu.space").gr(), (s) -> {
        switch (s) {
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
    
    public final HashSet<String> children = new HashSet<>();
    public void childInfo(String id, boolean has) {
      if (has) children.add(id);
      else children.remove(id);
    }
  }
  public RoomListNode.ExternalDirInfo asDir() {
    return spaceInfo;
  }
  public void setOfficialName(String name) {
    super.setOfficialName(name);
    if (spaceInfo!=null) spaceInfo.nameUpdated();
  }
  
  public void userMenu(Click c, int x, int y, String uid) {
    Popup.rightClickMenu(m.gc, m.ctx, "chat.profile.menu", cmd -> {
      switch (cmd) { default: Log.warn("chat", "Unknown menu option "+cmd); break;
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
}