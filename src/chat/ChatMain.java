package chat;

import chat.mx.*;
import chat.networkLog.*;
import chat.ui.*;
import chat.utils.UnreadInfo;
import dzaima.ui.eval.*;
import dzaima.ui.gui.*;
import dzaima.ui.gui.config.GConfig;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.*;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.*;
import dzaima.ui.node.types.editable.EditNode;
import dzaima.utils.*;
import dzaima.utils.JSON.*;
import dzaima.utils.options.*;
import io.github.humbleui.skija.*;
import libMx.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

public class ChatMain extends NodeWindow {
  public static final String DEFAULT_PROFILE = "accounts/profile.json";
  public static final Path LOCAL_CFG = Paths.get("local.dzcfg");
  
  public Options options;
  public boolean disableSaving = false;
  public final BiConsumer<String, Obj> dumpInitial;
  public final BiConsumer<String, Obj> dumpAll;
  public final Runnable networkLogTick;
  public final int artificialNetworkDelay; // in milliseconds
  public final boolean doRunModtools;
  
  public void insertNetworkDelay() {
    if (artificialNetworkDelay > 0) Tools.sleep(artificialNetworkDelay);
  }
  
  public enum Theme { light, dark }
  public final Box<Theme> theme;
  public final Path profilePath;
  private final Node msgs;
  public final RightPanel rightPanel;
  public final WeighedNode leftPanelWeighed;
  public final ScrollNode msgsScroll;
  public final Node inputPlace;
  public final Vec<ChatUser> users = new Vec<>();
  public final Node accountNode;
  public final Node actionbar, infobar;
  public View view;
  public MsgExtraNode msgExtra;
  public MsgExtraNode.HoverPopup hoverPopup;
  public final MuteState defaultMuteState = new MuteState(this) {
    protected UnreadInfo ownedInfo() { return UnreadInfo.NONE; }
    protected void updated() { }
  };
  {
    defaultMuteState.muted = true;
    defaultMuteState.mutePings = true;
  }
  
  public Chatroom room() {
    return view==null? null : view instanceof SearchView? null : view.room();
  }
  public LiveView liveView() {
    return view==null? null : view.baseLiveView();
  }
  public ChatTextArea input() {
    LiveView lv = liveView();
    return lv!=null? lv.input : null;
  }
  
  public ChatMain(GConfig gc, Ctx pctx, PNodeGroup g, Path profilePath, Box<Theme> theme, Obj loadedProfile, Options o) {
    super(gc, pctx, g, new WindowInit("chat"));
    this.options = o;
    this.theme = theme;
    if (o.has("--disable-saving")) disableSaving = true;
    dumpInitial = makeDumpConsumer(o.optList("--dump-initial-sync"));
    dumpAll = makeDumpConsumer(o.optList("--dump-all-sync"));
    String delay = o.optOne("--network-delay");
    artificialNetworkDelay = delay==null? 0 : Integer.parseInt(delay);
    boolean detailed = o.takeBool("--detailed-network-log");
    networkLogTick = NetworkLog.start(this, detailed, o.optOneInt("--network-log-minutes", detailed? 0 : 10));
    doRunModtools = !o.takeBool("--dry-run-modtools");
    
    msgs = base.ctx.id("msgs");
    accountNode = base.ctx.id("accounts");
    actionbar = base.ctx.id("actionbar");
    infobar = base.ctx.id("infobar");
    msgsScroll = (ScrollNode) base.ctx.id("msgsScroll");
    inputPlace = base.ctx.id("inputPlace");
    ((BtnNode) base.ctx.id("send")).setFn(c -> send());
    ((BtnNode) base.ctx.id("upload")).setFn(c -> {
      LiveView v = liveView();
      if (v!=null) v.upload();
    });
    ((BtnNode) base.ctx.id("roomInfo")).setFn(c -> {
      Chatroom r = room();
      if (r!=null) r.viewRoomInfo();
    });
    
    rightPanel = new RightPanel(this);
    leftPanelWeighed = (WeighedNode) base.ctx.id("leftPanelWeighed");
    
    this.profilePath = profilePath;
    
    Obj global = loadedProfile.obj("global", Obj.E);
    if (global.has("leftPanelWeight")) leftPanelWeighed.setWeight((float) global.num("leftPanelWeight"));
    if (global.has("rightPanelWeight")) rightPanel.setWeight((float) global.num("rightPanelWeight"));
    
    for (Obj c : loadedProfile.arr("accounts").objs()) {
      if (c.str("type").equals("matrix")) {
        addUser(new MxChatUser(this, c));
      } else throw new RuntimeException("Unknown account type '"+c.str("type")+"'");
    }
    cfgUpdated();
    updInfo();
  }
  
  
  
  public int imageSafety() { // 0-none; 1-safe; 2-all
    String p = gc.getProp("chat.loadImgs").val();
    if (p.equals("none")) return 0;
    if (p.equals("safe")) return 1;
    if (p.equals("all")) return 2;
    Log.warn("chat", "invalid chat.loadImgs value");
    return 2;
  }
  
  public void send() {
    ChatTextArea input = input();
    if (input==null) return;
    input.send();
  }
  
  public void setTheme(Theme t) {
    theme.set(t);
    gc.reloadCfg();
    requestSave();
  }
  
  public void addUser(ChatUser u) {
    users.add(u);
    if (accountNode.ch.sz!=0) accountNode.add(accountNode.ctx.makeHere(gc.getProp("chat.rooms.accountSepP").gr()));
    accountNode.add(u.node);
  }
  
  public int toLast; // 0-no; 1-smooth; 2-instant; 3-to highlighted
  public ChatEvent toHighlight;
  public void hideCurrent() {
    if (view!=null) view.hide();
    view = null;
    cHover = null;
    inputPlace.replace(0, new StringNode(ctx, ""));
    removeAllMessages();
    lastTimeStr = null;
  }
  public void toRoom(LiveView c) { toRoom(c, null); }
  public void toView(View v) { toView(v, null); }
  
  public void toRoom(LiveView c, ChatEvent toHighlight) {
    Log.fine("chat", "Moving to room "+c.title()+(toHighlight==null? "" : " with highlighting of "+toHighlight.id));
    if (c==view && gc.getProp("chat.read.doubleClickToRead").b()) c.markAsRead();
    hideCurrent();
    view = c;
    inputPlace.replace(0, c.inputPlaceContent());
    c.show();
    updActions();
    toLast = toHighlight!=null? 3 : 2;
    this.toHighlight = toHighlight;
  }
  public void toTranscript(TranscriptView v, ChatEvent toHighlight) {
    Log.fine("chat", "Moving to transcript of room "+v.room().officialName+(toHighlight==null? "" : " with highlighting of "+toHighlight.id));
    hideCurrent();
    view = v;
    if (toHighlight!=null) v.highlight(toHighlight);
    inputPlace.replace(0, v.baseLiveView().inputPlaceContent());
    v.show();
    updActions();
    toLast = 0;
  }
  
  public void toView(View v, ChatEvent toHighlight) {
    if (v instanceof TranscriptView) toTranscript((TranscriptView) v, toHighlight);
    else if (v instanceof LiveView) toRoom((LiveView) v, toHighlight);
    else Log.error("chat", "toView called with unexpected type");
  }
  public void updActions() {
    String info = "";
    ChatTextArea input = input();
    if (cHover!=null) info+= Time.localNearTimeStr(cHover.msg.time);
    info+= "\n";
    if (input!=null) {
      if (input.editing!=null) info+= "editing message";
      else if (input.replying!=null) info+= "replying to "+input.replying.senderDisplay();
    }
    Chatroom room = room();
    if (room!=null && room.typing.length()>0) {
      if (!info.endsWith("\n")) info+= "; ";
      info+= room.typing;
    }
    actionbar.replace(0, new StringNode(actionbar.ctx, info));
  }
  
  public String hoverURL = null;
  private String currentAction = null;
  public Runnable doAction(String msg) {
    currentAction = msg;
    updInfo();
    return () -> {
      currentAction = null;
      updInfo();
    };
  }
  public void updInfo() {
    ArrayList<String> infos = new ArrayList<>();
    if (currentAction!=null) infos.add(currentAction);
    if (hoverURL!=null) infos.add(hoverURL);
    StringBuilder info = new StringBuilder();
    for (String c : infos) {
      if (info.length()>0) info.append("; ");
      info.append(c);
    }
    if (info.length()==0 && disableSaving) info.append(options.has("--disable-saving")? "" : "Failed to load profile, ").append("saving disabled");
    infobar.replace(0, new StringNode(infobar.ctx, info.toString()));
  }
  
  public void updateCurrentViewTitle() {
    setCurrentViewTitle(view.title());
  }
  public void setCurrentViewTitle(String s) { // TODO use less?
    base.ctx.id("roomName").replace(0, new StringNode(base.ctx, s));
    updateTitle();
  }
  
  private int updInfoDelay;
  private long nextTimeUpdate;
  public int endDist;
  public void tick() {
    endDist = gc.em*20;
    
    if (updInfoDelay--==0) updActions();
    if (newHover) {
      if (pHover!=null) pHover.msg.markRel(false);
      if (cHover!=null) cHover.msg.markRel(true);
      pHover = cHover;
      newHover = false;
      if (cHover!=null) updActions();
      else updInfoDelay = 10;
    }
    
    super.tick();
    
    networkLogTick.run();
    for (ChatUser c : users) c.tick();
    
    if (view!=null) view.openViewTick();
    if (msgExtra!=null) msgExtra.tickExtra();
    if (hoverPopup!=null && hoverPopup.shouldClose()) hoverPopup.close();
    
    if (gc.lastNs>nextTimeUpdate) {
      insertLastTime();
      nextTimeUpdate = gc.lastNs + (long)60e9;
    }
    
    if (saveRequested) forceTrySaveNow();
  }
  
  private void forceTrySaveNow() {
    saveRequested = false;
    if (disableSaving) return;
    
    Val[] accounts = new Val[users.sz];
    for (int i = 0; i < accounts.length; i++) accounts[i] = users.get(i).data();
    Obj res = Obj.fromKV(
      "accounts", new Arr(accounts),
      "global", Obj.fromKV(
        "leftPanelWeight", leftPanelWeighed.getWeight(),
        "rightPanelWeight", rightPanel.getWeight(),
        "theme", theme.get().name()
      )
    );
    try {
      Files.write(profilePath, res.toString(2).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      Log.warn("chat", "Failed to write profile file");
      throw new RuntimeException("Failed to write profile file");
    }
  }
  
  public int tickDelta() {
    return focused? 1000/60 : 1000/5;
  }
  
  public void stopped() {
    forceTrySaveNow();
    for (ChatUser u : users) u.close();
  }
  
  
  
  private MsgNode cHover, pHover;
  private boolean newHover;
  public void hovered(MsgNode n) {
    cHover = n;
    newHover = true;
  }
  
  public void removeAllMessages() {
    newHover = true;
    msgs.clearCh();
  }
  
  private String lastTimeStr;
  public String currDelta() {
    if (!gc.getProp("chat.timeSinceLast").b()) return null;
    if (msgs.ch.sz==0 || !(view instanceof MxLiveView)) return null;
    int n = msgs.ch.sz-1;
    Node a = msgs.ch.get(n);
    if (!(a instanceof MsgNode)) { n--; if(n>=0) a = msgs.ch.get(n); }
    if (!(a instanceof MsgNode)) return null;
    ChatEvent at = ((MsgNode) a).msg;
    float h = Duration.between(at.time, Instant.now()).getSeconds()/3600f;
    if (h<1) return null;
    return timeDelta(h);
  }
  
  private final EnumProp lastTimeProp = EnumProp.cache("lastTime");
  public void removeLastTime() {
    nextTimeUpdate = 0;
    if (msgs.ch.sz>0) {
      Node n = msgs.ch.peek();
      Prop infoType = n.getPropN("infoType");
      if (infoType!=null && infoType==lastTimeProp) {
        msgs.ch.removeAt(msgs.ch.sz - 1);
        lastTimeStr = null;
      }
    }
  }
  public void insertLastTime() { // only called once, in tick
    String c = currDelta();
    if (!Objects.equals(c, lastTimeStr)) {
      lastTimeStr = c;
      removeLastTime();
      if (c==null) return;
      msgs.add(makeInfo(lastTimeProp, "chat.info.$textP", new StringNode(ctx, "The last message was posted "+c+" ago.")));
    }
  }
  
  public boolean atEnd() { return msgsScroll.atYE(5); }
  
  public Node makeInfo(EnumProp type, String cfg, Node body) {
    Node msg = ctx.make(gc.getProp("chat.info.mainP").gr());
    msg.setProp("infoType", type);
    msg.ctx.id("body").add(ctx.makeKV(gc.getProp(cfg).gr(), "body", body));
    return msg;
  }
  
  public void insertMessages(boolean atEnd, Vec<? extends ChatEvent> nds) { // expects all to be non-null
    if (nds.sz==0) return;
    if (atEnd) removeLastTime();
    newHover = true;
    Vec<Node> prep = new Vec<>();
    Node p = null;
    for (ChatEvent c : nds) {
      if (c.visible) {
        Log.warn("chat", "Event attempted to be shown twice: "+c.id);
        continue;
      }
      Node a = c.show(false, false);
      if (p!=null) {
        Node sep = handlePair(p, a, false);
        if (sep!=null) prep.add(sep);
      }
      p = prep.add(a);
    }
    if (msgs.ch.sz>0) {
      if (atEnd) {
        Node sep = handlePair(msgs.ch.peek(), prep.get(0), false);
        if (sep!=null) prep.insert(0, sep);
      } else {
        Node sep = handlePair(prep.peek(), msgs.ch.get(0), false);
        if (sep!=null) prep.add(sep);
      }
    }
    msgs.insert(atEnd? msgs.ch.sz : 0, prep);
    if (atEnd) msgsScroll.ignoreYE();
    else msgsScroll.ignoreYS();
  }
  
  public void addMessage(ChatEvent cm, boolean live) {
    addMessage(cm, live, false, false);
  }
  
  public void addMessage(ChatEvent cm, boolean live, boolean forceDate, boolean forContext) {
    removeLastTime();
    newHover = true;
    Node msg = cm.show(live, forContext);
    boolean atEnd = atEnd();
    if (msgs.ch.sz>0) {
      Node sep = handlePair(msgs.ch.peek(), msg, forceDate);
      if (sep!=null) msgs.add(sep);
    }
    msgs.add(msg);
    if (atEnd && toLast==0) toLast = 1;
    if (atEnd) msgsScroll.ignoreYE();
  }
  
  public void updMessage(ChatEvent ce, Node body, boolean newAtEnd) {
    boolean end = atEnd();
    newHover = true;
    ce.updBody(body);
    if (end && toLast!=1) toLast = Math.max(toLast, newAtEnd? 1 : 2);
  }
  
  public void unreadChanged() {
    updateTitle();
  }
  public void updateTitle() {
    Chatroom room = room();
    
    int otherNew = 0;
    boolean otherPing = false;
    UnreadInfo m = room==null? UnreadInfo.NONE : room.muteState.info();
    int currentNew = m.unread;
    boolean currentPing = m.ping;
    for (ChatUser u : users) {
      for (Chatroom r : u.rooms()) {
        if (r != room) {
          UnreadInfo state = r.muteState.info();
          otherNew+= state.unread;
          otherPing|= state.ping;
        }
      }
    }
    
    String ct;
    if (otherNew!=0 || currentNew!=0 || otherPing || currentPing) {
      StringBuilder b = new StringBuilder("(");
      
      if (currentNew!=0) b.append(currentNew);
      if (currentPing) b.append("*");
      
      if (otherNew!=0 || otherPing) b.append("+");
      
      if (otherNew!=0) b.append(otherNew);
      if (otherPing) b.append("*");
      
      b.append(") ");
      ct = b.toString();
    }
    else ct = "";
    
    if (view==null) setTitle(ct+"chat");
    else setTitle(ct+view.title());
  }
  
  
  
  private boolean saveRequested;
  public void requestSave() {
    if (!saveRequested) Log.info("chat", "account save requested"+(disableSaving? ", ignoring" : ""));
    saveRequested = true;
  }
  
  private final EnumProp laterProp = EnumProp.cache("later");
  // private static final SimpleDateFormat df = new SimpleDateFormat("E, MM d yyyy");
  private static final DateTimeFormatter df = DateTimeFormatter.ofPattern("EE, d MMM yyyy");
  public Node handlePair(Node a, Node b, boolean forceDate) {
    if (!(a instanceof MsgNode) || !(b instanceof MsgNode)) return null;
    ChatEvent at = ((MsgNode) a).msg;
    ChatEvent bt = ((MsgNode) b).msg;
    Duration d = Duration.between(at.time, bt.time);
    float h = d.getSeconds()/3600f;
    boolean before = h<0;
    if (before) h = -h;
    
    boolean between = !forceDate && h>=1 && gc.getProp("chat.timeBetween").b();
    
    LocalDate newDate = null;
    if (gc.getProp("chat.logDate").b() || forceDate) {
      LocalDate adt = Time.localDateTime(at.time).toLocalDate();
      LocalDate bdt = Time.localDateTime(bt.time).toLocalDate();
      if (!adt.equals(bdt)) newDate = bdt;
    }
    
    boolean merge = h<5f/60 && at.userEq(bt) && gc.getProp("chat.mergeMessages").b() && newDate==null;
    ((UserTagNode) b.ctx.id("user").ch.get(0)).setVis(!merge);
    Node padU = b.ctx.id("padU");
    padU.setProp("u", merge? new LenProp(gc, 0, "px") : gc.getProp("chat.msg.sep"));
    if (between || newDate!=null) {
      if (newDate==null) return makeInfo(laterProp, "chat.info.$textP", new StringNode(ctx, timeDelta(h)+(before? " earlier..?" : " later...")));
      else return makeInfo(laterProp, "chat.info.$titleP", new StringNode(ctx, df.format(newDate)));
    }
    return null;
  }
  
  private String timeDelta(float h) {
    int i = (int) h;
    String k = "hour";
    if (h>=24) {
      i/= 24;
      k = "day";
    }
    return i+" "+k+(i==1?"":"s");
  }
  
  public void viewImage(Animation anim) {
    new Popup(this) {
      protected void unfocused() { close(); }
      protected Rect fullRect() { return centered(base.ctx.vw(), 0.8, 0.9); }
      
      ImageViewerNode img;
      protected void setup() {
        img = (ImageViewerNode) node.ctx.id("viewer");
        img.setAnim(anim);
        img.ctx.focus(img);
        img.zoomToFit();
      }
      
      protected boolean key(Key key, KeyAction a) { img.mRedraw();
        switch (gc.keymap(key, a, "imageViewer")) { default: return false;
          case "exit": close(); return true;
          case "pixelFit": img.center(); img.zoom(img.w/2, img.gh()/2, 1); return true;
          case "playPause": img.playPause(); return true;
          case "nextFrame": img.nextFrame(); return true;
          case "prevFrame": img.prevFrame(); return true;
          case "toStart": img.toFrame(0); return true;
        }
      }
    }.open(gc, ctx, gc.getProp("imageViewer.ui").gr());
  }
  
  
  public void search() {
    if (room()==null) return;
    View s = view.getSearch();
    if (s!=null) toViewDirect(s);
  }
  
  public void toViewDirect(View v) {
    hideCurrent();
    view = v;
    view.show();
  }
  
  
  
  public boolean onCancel(Key key, KeyAction a, Runnable run) {
    return onCancel(key, a, () -> { run.run(); return true; });
  }
  public boolean onCancel(Key key, KeyAction a, Supplier<Boolean> run) {
    return gc.keymap(key, a, "chat").equals("cancel") && run.get();
  }
  
  public boolean chatTyped(int codepoint) {
    if (view==null) return false;
    return view.typed(codepoint);
  }
  
  public boolean key(Key key, int scancode, KeyAction a) {
    String chatAction = gc.keymap(key, a, "chat");
    switch (chatAction) {
      case "fontPlus":  gc.setEM(gc.em+1); return true;
      case "fontMinus": gc.setEM(gc.em-1); return true;
      case "roomUp": case "roomDn": {
        boolean up = chatAction.equals("roomUp");
        Chatroom prev = room();
        Chatroom res = null;
        search: {
          if (prev==null) break search;
          ChatUser u = prev.user();
          int i = users.indexOf(u);
          int j = u.roomListNode.findRoomClosest(prev);
          if (j==-1) break search;
          int delta = up? -1 : 1;
          while (true) {
            res = u.roomListNode.nextRoom(j, delta);
            if (res!=null) break;
            i+= delta;
            if (up? i<0 : i>=users.sz) break;
            u = users.get(i);
            j = up? u.roomListNode.ch.sz : -1;
          }
        }
        if (res==null) res = edgeRoom(!up);
        if (res!=null) toRoom(res.mainView());
        return true;
      }
      case "search": {
        search();
        return true;
      }
      case "reloadCfg": {
        try { gc.reloadCfg(); }
        catch (Throwable e) {
          Log.error("config reload", "Failed to load config:");
          Window.onError(this, e, "config reload", null);
        }
        return true;
      }
      case "openDevtools": createTools(); return true;
      case "toggleLegacyStringRendering": StringNode.PARAGRAPH_TEXT^= true; gc.cfgUpdated(); return true;
    }
    
    if (rightPanel.key(key, a)) return true;
    if (view!=null && view.navigationKey(key, a)) return true;
    
    if (super.key(key, scancode, a)) return true;
    
    if (view==null) return transferToInput(key, a, input());
    else return view.actionKey(key, a);
  }
  
  public boolean transferToInput(Key key, KeyAction a, EditNode node) {
    if (node==null || !a.press || key.isModifier() || focusNode() instanceof EditNode) return false;
    focus(node);
    return node.keyF(key, 0, a);
  }
  
  private Chatroom edgeRoom(boolean up) {
    for (int i0 = 0; i0 < users.sz; i0++) {
      int i = up? i0 : users.sz-i0-1;
      ChatUser u = users.get(i);
      Chatroom f = u.roomListNode.nextRoom(up? -1 : u.roomListNode.ch.sz, up? 1 : -1);
      if (f!=null) return f;
    }
    return null;
  }
  
  public static void main(String[] args) {
    Windows.setManager(Windows.Manager.JWM);
    
    AutoOptions o = new AutoOptions();
    o.argBool("--disable-saving", "Disable saving profile");
    o.argString("--dump-initial-sync", "Dump initial sync JSON of rooms with matching ID");
    o.argString("--dump-all-sync", "Dump all sync JSON of rooms with matching ID");
    o.argString("--network-delay", "Introduce artificial network delay, in milliseconds");
    o.argBoolRun("--disable-threads", "Disable structuring messages by threads", () -> MxMessage.supportThreads = false);
    o.argBool("--detailed-network-log", "Preserve all info about network requests");
    o.argString("--network-log-minutes", "Time to preserve network log for (0 for forever). Default: forever in detailed mode, 10 minutes otherwise");
    o.argBool("--no-lazy-load-members", "Disable lazy member list loading");
    o.argBool("--dry-run-modtools", "Disable mod tools doing any actions (except unbanning)");
    o.argBool("--no-initial-messages", "Don't load any messages on startup");
    o.autoDebug(Log.Level.WARN);
    o.acceptLeft(1);
    o.autoHelp();
    Vec<String> left = o.run(args);
    
    Path profilePath = Paths.get(left.sz==0? DEFAULT_PROFILE : left.get(0));
    Obj loadedProfile;
    try {
      loadedProfile = JSON.parseObj(new String(Files.readAllBytes(profilePath), StandardCharsets.UTF_8));
    } catch (IOException e) {
      Log.error("chat", "Failed to load profile");
      System.exit(1); throw new IllegalStateException();
    }
    Box<Theme> theme = new Box<>(Theme.valueOf(loadedProfile.obj("global", Obj.E).str("theme", "dark")));
    
    Utils.logFn = Log::fine;
    Utils.warnFn = Log::warn;
    
    Windows.start(mgr -> {
      BaseCtx ctx = Ctx.newCtx();
      ctx.put("msgBorder", MsgBorderNode::new);
      ctx.put("hideOverflow", HideOverflowNode::new);
      ctx.put("imageViewer", ImageViewerNode::new);
      ctx.put("roomList", RoomListNode::new);
      ctx.put("clickableText", Extras.ClickableTextNode::new);
      ctx.put("nameEditField", RoomEditing.NameEditFieldNode::new);
      ctx.put("chatfield", ChatTextFieldNode::new);
      ctx.put("copymenu", CopyMenuNode::new);
      ctx.put("account", AccountNode::new);
      
      GConfig gc = GConfig.newConfig(gc0 -> {
        gc0.addCfg(() -> Tools.readRes("chat.dzcfg"));
        gc0.addCfg(() -> {
          if (theme.get()==Theme.light) return Tools.readRes("light.dzcfg");
          return "";
        });
        gc0.addCfg(() -> {
          if (Files.exists(LOCAL_CFG)) return Tools.readFile(LOCAL_CFG);
          return "";
        });
      });
      
      mgr.start(new ChatMain(gc, ctx, gc.getProp("chat.ui").gr(), profilePath, theme, loadedProfile, o.o));
    });
  }
  private BiConsumer<String, Obj> makeDumpConsumer(Vec<OptionItem> items) {
    if (items.sz == 0) return (s, o) -> {};
    return (id, obj) -> {
      if (items.some(c -> id.contains(c.v))) {
        Log.info("mx dump", "Dump of sync of "+id+":");
        Log.info("mx dump", obj.toString(2));
      }
    };
  }
  
  public boolean networkViewOpen() {
    return view instanceof BasicNetworkView;
  }
  
  public long readMinViewMs;
  public float altViewMult;
  public int colMyNick, colMyPill;
  public int[] colOtherNicks;
  public int[] colOtherPills;
  public int[] folderColors;
  public Paint msgBorder;
  private static int[] colorList(Prop p) {
    if (p instanceof ColProp) {
      return new int[]{p.col()};
    } else {
      Vec<PNode> l = p.gr().ch;
      int[] res = new int[l.sz];
      for (int i = 0; i < res.length; i++) {
        int c = ColorUtils.parsePrefixed(((PNode.PNodeStr) l.get(i)).s);
        res[i] = c;
      }
      return res;
    }
  }
  @Override public void cfgUpdated() { super.cfgUpdated();
    colMyNick = gc.getProp("chat.userCols.myNick").col();
    colMyPill = gc.getProp("chat.userCols.myPill").col();
    colOtherNicks = colorList(gc.getProp("chat.userCols.otherNicks"));
    colOtherPills = colorList(gc.getProp("chat.userCols.otherPills"));
    folderColors = ChatMain.colorList(gc.getProp("chat.folder.colors"));
    readMinViewMs = (long) (gc.getProp("chat.read.minView").d()*1000);
    altViewMult = gc.getProp("chat.read.altViewMul").f();
    
    msgBorder = new Paint().setColor(gc.getProp("chat.msg.border").col()).setPathEffect(PathEffect.makeDash(new float[]{1, 1}, 0));
    for (ChatUser u : users) {
      for (Chatroom r : u.rooms()) {
        r.cfgUpdated();
      }
    }
  }
  
  public static boolean keyFocus(NodeWindow w, Key key, KeyAction a) {
    Node f = w.focusNode();
    if (f==null) return false;
    return f.keyF(key, 0, a);
  }
}