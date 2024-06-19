package chat;

import chat.mx.*;
import chat.networkLog.NetworkLog;
import chat.ui.*;
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
import java.util.function.BiConsumer;

public class ChatMain extends NodeWindow {
  public static final String DEFAULT_PROFILE = "accounts/profile.json";
  public static final Path LOCAL_CFG = Paths.get("local.dzcfg");
  
  public Options options;
  public boolean disableSaving = false;
  public final BiConsumer<String, Obj> dumpInitial;
  public final BiConsumer<String, Obj> dumpAll;
  public final Runnable networkLogTick;
  public final int artificialNetworkDelay; // in milliseconds
  
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
    protected int ownedUnreads() { return 0; }
    protected boolean ownedPings() { return false; }
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
    networkLogTick = NetworkLog.start(this, o.takeBool("--detailed-network-log"));
    
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
    LiveView live = v.baseLiveView();
    inputPlace.replace(0, live==null? new StringNode(ctx, "TODO thread") : live.input);
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
      else if (input.replying!=null) info+= "replying to "+(input.replying.username);
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
    if (info.length()==0 && disableSaving) info.append("Failed to load profile, saving disabled");
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
  
  public boolean draw(Graphics g, boolean full) {
    return super.draw(g, full);
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
  public MsgNode createMessage(ChatEvent cm, boolean asContext) {
    MsgNode r = new MsgNode(msgs.ctx.shadow(), cm.type(), cm, asContext);
    r.ctx.id("user").replace(0, new UserTagNode(this, cm));
    return r;
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
  
  private final EnumProp lastTimeProp = new EnumProp("lastTime");
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
    if (!atEnd) msgsScroll.ignoreYS();
    if ( atEnd) msgsScroll.ignoreYE();
  }
  
  public Node getMsgBody(Node n) {          Node b = n.ctx.id("body"); return b.ch.get(b.ch.sz-1); }
  public void setMsgBody(Node n, Node ct) { Node b = n.ctx.id("body"); b.replace(b.ch.sz-1, ct); }
  
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
  private Node makeExtra(ChatEvent ce) {
    HashMap<String, Integer> rs = ce.getReactions();
    HashSet<String> vs = ce.getReceipts(view);
    boolean edit = newEdit(ce) && ce.edited;
    boolean hasThread = ce.startsThread(view);
    return rs!=null || vs!=null || edit || hasThread? new MsgExtraNode(ctx, ce, rs, vs, hasThread) : new InlineNode.LineEnd(ctx, false);
  }
  private static final Props.Gen col_ibeam = Props.keys("ibeam","color");
  private Node mkSText(ChatEvent e) {
    if (e.n==null || !e.n.asContext) return new STextNode(ctx, true);
    return new STextNode(ctx, col_ibeam.values(EnumProp.TRUE, gc.getProp("chat.search.ctx.color")));
  }
  private boolean newEdit(ChatEvent e) {
    return e.n.ctx.idNullable("edit")==null;
  }
  public void updMessage(ChatEvent ce, Node body, boolean live) { // TODO move to ChatEvent?
    Node msg = ce.n;
    boolean end = atEnd();
    newHover = true;
    if (ce.edited && !newEdit(ce)) {
      Node n = msg.ctx.id("edit");
      if (n.ch.sz==0) n.add(n.ctx.make(n.gc.getProp("chat.icon.editedP").gr()));
    }
    Node nb;
    if (ce.target!=null) {
      nb = mkSText(ce);
      nb.add(new LinkBtn(ctx, nb.ctx.makeHere(gc.getProp("chat.icon.replyP").gr()), ce));
      nb.add(body);
    } else if (body instanceof InlineNode) {
      nb = mkSText(ce);
      nb.add(body);
    } else nb = body;
    if (newEdit(ce) && ce.edited) nb.add(nb.ctx.make(gc.getProp("chat.msg.editedEndP").gr()));
    nb.add(makeExtra(ce));
    setMsgBody(msg, nb);
    if (end && toLast!=1) toLast = Math.max(toLast, live? 1 : 2);
  }
  public void updateExtra(ChatEvent e) { // TODO move to ChatEvent?
    if (!e.visible) return;
    Node b = getMsgBody(e.n);
    b.replace(b.ch.sz-1, makeExtra(e));
  }
  
  public void unreadChanged() {
    updateTitle();
  }
  public void updateTitle() {
    Chatroom room = room();
    
    int otherNew = 0;
    int currentNew = room==null? 0 : room.muteState.unreads();
    boolean currentPing = room!=null && room.muteState.anyPing();
    boolean otherPing = false;
    for (ChatUser u : users) {
      for (Chatroom r : u.rooms()) {
        if (r != room) {
          otherNew+= r.muteState.unreads();
          otherPing|= r.muteState.anyPing();
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
  
  public void updateUnread() { // TODO how needed is this?
    if (view instanceof LiveView) view.room().unreadChanged();
    else unreadChanged();
  }
  
  
  private boolean saveRequested;
  public void requestSave() {
    if (!saveRequested) Log.info("chat", "account save requested"+(disableSaving? ", ignoring" : ""));
    saveRequested = true;
  }
  
  private final EnumProp laterProp = new EnumProp("later");
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
  
  
  static class LinkBtn extends PadCNode {
    private final ChatEvent m;
    public LinkBtn(Ctx ctx, Node ch, ChatEvent m) {
      super(ctx, ch, 0, .05f, .1f, .1f);
      this.m = m;
    }
    public void hoverS() { ctx.vw().pushCursor(CursorType.HAND); }
    public void hoverE() { ctx.vw().popCursor(); }
    
    public void mouseStart(int x, int y, Click c) {
      super.mouseStart(x, y, c);
      c.register(this, x, y);
    }
    public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
    public void mouseUp(int x, int y, Click c) {
      if (visible && gc.isClick(c)) m.toTarget();
    }
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
  
  
  public boolean chatKey(Key key, int scancode, KeyAction a) {
    if (view==null) return false;
    return view.key(key, scancode, a);
  }
  
  public boolean chatTyped(int codepoint) {
    if (view==null) return false;
    return view.typed(codepoint);
  }
  
  public void focused() { super.focused();
    updateUnread();
  }
  
  public boolean key(Key key, int scancode, KeyAction a) {
    ChatTextArea input = input();
    if (input!=null && input.globalKey(key, a)) return true;
    if ((input==null && view!=null || view instanceof SearchView) && view.key(key, scancode, a)) return true; // TODO don't special-case SearchView? 
    
    String name = gc.keymap(key, a, "chat");
    switch (name) {
      case "fontPlus":  gc.setEM(gc.em+1); return true;
      case "fontMinus": gc.setEM(gc.em-1); return true;
      case "roomUp": case "roomDn": {
        boolean up = name.equals("roomUp");
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
    }
    
    if (a.typed) {
      if (key.k_f5()) {
        try { gc.reloadCfg(); }
        catch (Throwable e) {
          Log.error("config reload", "Failed to load config:");
          Window.onError(this, e, "config reload", null);
        }
        return true;
      }
    }
    if (a.press) {
      if (key.k_f12()) { createTools(); return true; }
      if (key.k_f2()) { StringNode.PARAGRAPH_TEXT^= true; gc.cfgUpdated(); return true; }
    }
    if (super.key(key, scancode, a)) return true;
    if (rightPanel.key(key, a)) return true;
    if (a.press && !key.isModifier() && !(focusNode() instanceof EditNode)) {
      if (input!=null && input.visible) focus(input);
      else if (view instanceof SearchView) focus(((SearchView) view).textInput());
    }
    return super.key(key, scancode, a);
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
    AutoOptions o = new AutoOptions();
    o.argBool("--disable-saving", "Disable saving profile");
    o.argString("--dump-initial-sync", "Dump initial sync JSON of rooms with matching ID");
    o.argString("--dump-all-sync", "Dump all sync JSON of rooms with matching ID");
    o.argString("--network-delay", "Introduce artificial network delay, in milliseconds");
    o.argBoolRun("--disable-threads", "Disable structuring messages by threads", () -> MxMessage.supportThreads = false);
    o.argBool("--detailed-network-log", "Preserve all info about network requests");
    o.argBool("--no-lazy-load-members", "Disable lazy member list loading");
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
    
    MxServer.logFn = Log::fine;
    MxServer.warnFn = Log::warn;
    Windows.setManager(Windows.Manager.JWM);
    // Windows.setManager(Windows.Manager.LWJGL);
    
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