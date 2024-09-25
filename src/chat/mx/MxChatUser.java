package chat.mx;

import chat.*;
import chat.mx.MediaThread.MediaRequest;
import chat.mx.MxChatroom.MyStatus;
import chat.networkLog.NetworkLog;
import chat.ui.*;
import chat.ui.Extras.LinkType;
import chat.utils.*;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.types.StringNode;
import dzaima.ui.node.types.editable.code.CodeAreaNode;
import dzaima.ui.node.types.editable.code.langs.Lang;
import dzaima.utils.*;
import dzaima.utils.JSON.*;
import libMx.*;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.zip.*;

public class MxChatUser extends ChatUser {
  public final Obj data;
  public MxServer s = null;
  public AtomicReference<MxServer> s_atomic = new AtomicReference<>();
  public MxLogin u = null;
  
  public boolean lazyLoadUsers;
  public MxSync2 sync;
  public String currentSyncToken;
  
  public final HashSet<String> autoban = new HashSet<>();
  public final HashMap<String, MxChatroom> roomMap = new HashMap<>();
  public final Collection<MxChatroom> roomSet = roomMap.values();
  
  private final ConcurrentLinkedQueue<Runnable> primary = new ConcurrentLinkedQueue<>();
  private final LinkedBlockingDeque<Runnable> network = new LinkedBlockingDeque<>();
  
  public static void logGet(String logText, String url) {
    Utils.log("file", logText+" "+url);
  }
  private static byte[] rawCachedGet(ChatMain m, String logText, String url) {
    Utils.LoggableRequest rq = new NetworkLog.CustomRequest(Utils.RequestType.GET, url);
    Utils.requestLogger.got(rq, "new", null);
    byte[] res = CacheObj.compute(url, () -> {
      try {
        logGet(logText, url);
        Utils.requestLogger.got(rq, "not in cache, requesting", null);
        return Tools.get(url, true);
      } catch (RuntimeException e) {
        Log.warn(logText, "Failed to load " + url);
        m.insertNetworkDelay();
        return null;
      }
    }, () -> Utils.requestLogger.got(rq, "received result from cache", null));
    Utils.requestLogger.got(rq, "result", res);
    return res;
  }
  
  public String upload(byte[] data, String name, String mime) {
    // TODO replace with s.request
    String location = "/_matrix/media/r0/upload?filename="+Utils.toURI(name)+"&access_token="+s.gToken;
    String req = s.url + location;
    NetworkLog.CustomRequest rq = new NetworkLog.CustomRequest(Utils.RequestType.POST, location);
    Utils.requestLogger.got(rq, "new", s);
    Utils.RequestRes r = Utils.postPut("POST", new Utils.RequestParams(null), req, data, mime);
    String res = new String(r.bytes, StandardCharsets.UTF_8);
    Utils.requestLogger.got(rq, "result", r.code+": "+res);
    return JSON.parseObj(res).str("content_uri", "");
  }
  
  public void queueNetwork(Runnable r) { network.add(r); }
  @FunctionalInterface public interface Request<T> { T get() throws Throwable; }
  
  public <T> void queueRequest(Request<T> onNetwork, Consumer<T> onPrimary) {
    queueNetwork(() -> {
      T r;
      try { r = onNetwork.get(); }
      catch (Throwable e) {
        if (Tools.isAnyInterrupted(e)) return;
        Log.stacktrace("mx queueRequest", e);
        r = null;
      }
      T finalR = r;
      primary.add(() -> onPrimary.accept(finalR));
    });
  }
  
  private final Thread networkThread = Tools.thread(() -> {
    while (true) {
      try {
        network.take().run();
      } catch (Throwable e) {
        if (Tools.isAnyInterrupted(e)) return;
        Log.stacktrace("mx networkThread", e);
      }
    }
  }, true);
  private final MediaThread media = new MediaThread();
  
  public final Vec<Command> commands = new Vec<>();
  
  public MxChatUser(ChatMain m, Obj dataIn) {
    super(m);
    this.data = dataIn;
    
    commands.add(new Command.SimpleArgCommand("set-nick-global", left -> queueNetwork(() -> u.setGlobalNick(left))));
    commands.add(new Command.SimpleArgCommand("goto", left -> {
      if (left.startsWith("!") || left.startsWith("#")) {
        MxChatroom r = findRoom(left);
        if (r!=null) {
          m.toRoom(r.mainView());
          return;
        }
      }
      openLink(left, Extras.LinkInfo.UNK);
    }));
    commands.add(new Command.SimpleTestCommand("theme", left -> {
      switch (left) {
        case "light": m.setTheme(ChatMain.Theme.light); return true;
        case "dark": m.setTheme(ChatMain.Theme.dark); return true;
        default: return false;
      }
    }));
    commands.add(new Command.SimplePlainCommand("network-log", () -> NetworkLog.open(this)));
    commands.add(new MxChatroom.IdArgCommand("join", id -> queueNetwork(() -> u.join(u.s.room(id)))));
    
    MxLoginMgr login = new MxLoginMgr() {
      public String getServer()   { return data.str("server"); }
      public String getUserID()   { return data.str("userid"); }
      public String getPassword() { return data.str("password"); }
      public String getToken()    { return data.str("token", null); }
      public void updateToken(String token) {
        data.put("token", new JSON.Str(token));
        m.requestSave();
      }
    };
    for (String c : data.arr("autoban", Arr.E).strs()) autoban.add(c);
    lazyLoadUsers = !m.options.takeBool("--no-lazy-load-members");
    int msgsToPreload = m.options.takeBool("--no-initial-messages")? 0 : 50;
    node.ctx.id("server").replace(0, new StringNode(node.ctx, login.getServer().replaceFirst("^https?://", "")));
    queueNetwork(() -> {
      MxServer s0 = login.create();
      s_atomic.set(s0);
      MxLogin u0 = login.login(s0);
      if (u0==null) {
        Log.error("mx", "Failed to log in");
        return;
      }
      
      String name = u0.user().globalName();
      primary.add(() -> {
        s = s0;
        u = u0;
        node.ctx.id("name").replace(0, new StringNode(node.ctx, name));
      });
      
      Obj j = u0.s.requestV3("sync").prop("filter", MxServer.syncFilter(msgsToPreload, lazyLoadUsers, true).toString()).token(u0.token).get().runJ();
      Log.info("mx stats", () -> "Initial sync of "+u0.uid+": "+j.toString().length()+" characters");
      primary.add(() -> {
        try {
          Obj rooms = j.obj("rooms", Obj.E);
          for (Entry e : rooms.obj("join", Obj.E).entries()) {
            roomMap.put(e.k, new MxChatroom(this, e.k, e.v.obj(), MyStatus.JOINED));
          }
          for (Entry e : rooms.obj("invite", Obj.E).entries()) {
            roomMap.put(e.k, new MxChatroom(this, e.k, e.v.obj(), MyStatus.INVITED));
          }
          
          Arr storedStructure = data.arr("roomStructure", Arr.E);
          restoreTree(storedStructure, data.arr("roomOrder", null));
        } catch (Throwable t) {
          m.disableSaving = true;
          m.updInfo();
          Log.error("mx", "Failed to load room structure. Not saving any profile changes!");
          Log.stacktrace("mx", t);
        }
        if (data.has("roomOrder")) data.remove("roomOrder");
        saveRooms();
      });
      
      MxSync2 sync0 = new MxSync2(s0, j.str("next_batch"), MxServer.syncFilter(-1, lazyLoadUsers, true));
      sync0.start();
      sync = sync0;
    });
  }
  
  public void restoreTree(JSON.Arr state, JSON.Arr legacyOrder) {
    RoomTree.restoreTree(this, state, legacyOrder);
  }
  
  
  public void autobanRemoveMessage(MxChatEvent ev) {
    String uid = ev.senderID();
    if (!autoban.contains(uid)) throw new RuntimeException();
    if (!ev.r.powerLevels.can(u.uid, PowerLevelManager.Action.REDACT)) return;
    Log.warn("mx auto-ban", "auto-removing message "+ev.id+" from "+ev.senderID()+" in "+ev.r.prettyID());
    if (m.doRunModtools) queueNetwork(() -> ev.r.delete(ev));
    autobanMember(uid, ev.r);
  }
  public void autobanMember(String uid, MxChatroom r) {
    if (!autoban.contains(uid)) throw new RuntimeException();
    if (!r.powerLevels.can(u.uid, PowerLevelManager.Action.BAN)) return;
    Log.warn("mx auto-ban", "auto-banning "+uid+" in "+r.prettyID());
    if (m.doRunModtools) queueNetwork(() -> r.r.ban(uid, null));
  }
  
  public void autobanUpdated() {
    if (autoban.remove(u.uid)) Log.warn("mx", "don't autoban yourself!!");
    data.put("autoban", new Arr(Vec.ofCollection(autoban).map(Str::new).toArray(new Val[0])));
    m.requestSave();
  }
  
  public void saveRooms() {
    data.put("roomStructure", RoomTree.saveTree(roomListNode));
    m.requestSave();
  }
  
  public Vec<MxChatroom> rooms() {
    Vec<MxChatroom> r = new Vec<>();
    for (MxChatroom c : roomSet) r.add(c);
    return r;
  }
  
  public void tick() {
    while (true) {
      Runnable c = primary.poll(); if(c==null) break;
      try {
        c.run();
      } catch (Throwable t) { Log.stacktrace("mx primaryQueue", t); }
    }
    media.tick();
    
    if (sync==null) return;
    
    while (true) {
      Obj m = sync.poll(); if (m==null) break;
      currentSyncToken = m.str("next_batch");
      Box<Boolean> newRooms = new Box<>(false);
      
      BiConsumer<MyStatus, Obj> processRoomList = (status, data) -> {
        for (JSON.Entry k : data.entries()) {
          MxChatroom room = roomMap.get(k.k);
          if (room==null) {
            MxChatroom r = new MxChatroom(this, k.k, k.v.obj(), status);
            preRoomListChange();
            roomMap.put(k.k, r);
            roomListNode.add(r.node); // TODO place in space if appropriate
            newRooms.set(true);
          } else room.update(status, k.v.obj(), true);
        }
      };
      
      Obj rooms = m.obj("rooms", Obj.E);
      processRoomList.accept(MyStatus.JOINED, rooms.obj("join", Obj.E));
      processRoomList.accept(MyStatus.INVITED, rooms.obj("invite", Obj.E));
      processRoomList.accept(MyStatus.LEFT, rooms.obj("leave", Obj.E));
      
      if (newRooms.get()) roomListChanged();
    }
    
    for (MxChatroom c : roomSet) c.tick();
  }
  
  public void close() {
    if (sync!=null) sync.stop(); // will wait for max 30s more but ¯\_(ツ)_/¯
    networkThread.interrupt();
  }
  
  public String id() {
    return u.uid;
  }
  
  public Obj data() {
    return data;
  }
  
  
  
  public Promise<byte[]> queueGet(String uri) {
    return Promise.create(set -> media.request(parseURI(uri, null).requestFull(), r -> primary.add(() -> set.set(r)), ()->true));
  }
  
  public String linkMenu(String url) {
    return MxServer.isMxc(url)? "chat.mxcMenu" : super.linkMenu(url);
  }
  
  public URIInfo parseURI(String uri, Obj info) {
    int safety = m.imageSafety();
    if (MxServer.isMxc(uri)) {
      boolean hasThumbnail = info==null || !info.str("mimetype", "").equals("image/gif");
      return new URIInfo(uri, info, safety>0, hasThumbnail) {
        public MediaRequest requestFull() {
          return new MediaRequest.FromMxRequest(s.mxcDownloadRequest(uri));
        }
        public MediaRequest requestThumbnail() {
          if (!hasThumbnail) throw new IllegalStateException();
          return new MediaRequest.FromMxRequest(s.mxcThumbnailRequest(uri, m.gc.getProp("chat.image.maxW").len(), m.gc.getProp("chat.image.maxH").len(), MxServer.ThumbnailMode.SCALE));
        }
      };
    }
    return new URIInfo(uri, info, safety>1, false) {
      public MediaRequest requestFull() { return new MediaRequest.FromURL(uri); }
      public MediaRequest requestThumbnail() { throw new IllegalStateException(); }
    };
  }
  
  public void loadImg(URIInfo info, boolean acceptThumbnail,
                      Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, Supplier<Boolean> stillNeeded) {
    boolean doThumbnail = acceptThumbnail && info.hasThumbnail;
    loadImg(info, doThumbnail? info.requestThumbnail() : info.requestFull(), doThumbnail, loaded, ctor, stillNeeded);
  }
  public void loadImg(URIInfo info, MediaRequest rq, boolean isThumbnail,
                      Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, Supplier<Boolean> stillNeeded) {
    media.request(
      rq,
      data -> primary.add(() -> {
        Extras.LinkInfo linkInfo = new Extras.LinkInfo(LinkType.IMG, isThumbnail? null : data, info.obj);
        loaded.accept(HTMLParser.inlineImage(this, info.uri, linkInfo, ctor, data));
      }),
      stillNeeded
    );
  }
  
  public MxChatroom findRoom(String name) {
    if (name.startsWith("!")) {
      for (MxChatroom c : roomSet) if (c.r.rid.equals(name)) return c;
    } else if (name.startsWith("#")) {
      
      for (MxChatroom c : roomSet) if (name.equals(c.canonicalAlias)) return c;
      for (MxChatroom c : roomSet) for (String a : c.altAliases) if (name.equals(a)) return c;
    }
    return null;
  }
  
  private void openURIGeneric(String uri, Extras.LinkInfo info) {
    if (MxServer.isMxc(uri)) {
      String mime = info.mime();
      if (mime.startsWith("video/") || mime.startsWith("audio/") || mime.startsWith("image/") || mime.equals("application/pdf")) {
        downloadTmpAndOpen(uri, info, () -> {});
      } else {
        downloadToSelect(uri, info, () -> {});
      }
    } else {
      m.gc.openLink(uri);
    }
  }
  public static final Counter popupCounter = new Counter();
  public void openLink(String uri, Extras.LinkInfo info) {
    if (info.type==LinkType.EXT) {
      openURIGeneric(uri, info);
      return;
    }
    
    // known event/room
    if (uri.startsWith("https://matrix.to/#/")) {
      try {
        URI u = new URI(uri);
        String fr = u.getFragment().substring(1);
        int pos = fr.indexOf('?');
        if (pos!=-1) fr = fr.substring(0, pos);
        String[] parts = Tools.split(fr, '/');
        int n = parts.length;
        while (n>0 && parts[n-1].isEmpty()) n--;
        if (n==1) {
          MxChatroom r = findRoom(parts[0]);
          if (r!=null) {
            m.toRoom(r.mainView());
            return;
          }
        }
        if (n==2) {
          MxChatroom r = findRoom(parts[0]);
          String msgId = parts[1];
          if (r!=null) {
            r.highlightMessage(msgId, b -> {
              if (!b) openURIGeneric(uri, info);
            }, false);
            return;
          }
        }
      } catch (URISyntaxException ignored) { }
    }
    
    // displayable image/animation
    if (m.gc.getProp("chat.internalImageViewer").b() && (info.type==LinkType.IMG || uri.contains("/_matrix/media/"))) {
      int action = popupCounter.next();
      Consumer<byte[]> showImg = d -> {
        if (popupCounter.superseded(action)) return;
        if (d!=null) {
          Animation anim = new Animation(d);
          if (anim.valid) {
            m.viewImage(anim);
            return;
          }
        }
        openURIGeneric(uri, info);
      };
      
      if (info.linkedData!=null) {
        showImg.accept(info.linkedData);
        return;
      }
      
      Runnable done = m.doAction("loading image...");
      Consumer<byte[]> onIsImg = isImg -> {
        if (isImg!=null && isImg[0]==1) {
          queueGet(uri).then(d -> {
            done.run();
            showImg.accept(d);
          });
        } else {
          done.run();
          openURIGeneric(uri, info);
        }
      };
      
      if (MxServer.isMxc(uri)) onIsImg.accept(new byte[]{(byte) (info.type==LinkType.IMG? 1 : 0)});
      else queueRequest(() -> CacheObj.compute("head_isImage\0"+uri, () -> {
        try {
          Utils.log("head", uri);
          HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
          c.setRequestMethod("HEAD");
          String[] ct = new String[1];
          c.getHeaderFields().forEach((k, v) -> {
            if ("Content-Type".equals(k) && v.size()==1) ct[0] = v.get(0);
          });
          c.disconnect();
          m.insertNetworkDelay();
          
          return new byte[]{(byte)(ct[0]!=null && ct[0].startsWith("image/")? 1 : 0)};
        } catch (Throwable e) {
          Log.stacktrace("mx image type", e);
          return null;
        }
      }, () -> {}), onIsImg);
      
      return;
    }
    
    // https://dzaima.github.io/paste
    Pair<String, String> parts = HTMLParser.urlFrag(HTMLParser.fixURL(uri));
    paste: if (m.gc.getProp("chat.internalPasteViewer").b() && parts.a.equals("https://dzaima.github.io/paste")) {
      if (parts.b==null) break paste;
      String[] ps = Tools.split(parts.b, '#');
      if (ps.length!=1 && ps.length!=2) break paste;
      
      String lang = ps.length==1? "text" : pasteMap.get(ps[1]);
      if (lang==null) break paste;
      
      byte[] inflated;
      try {
        byte[] deflated = Base64.getDecoder().decode(ps[0].substring(1).replace('@', '+'));
        Inflater i = new Inflater(true);
        i.setInput(deflated);
        
        byte[] buf = new byte[1024];
        ByteVec v = new ByteVec();
        while (true) {
          int l = i.inflate(buf);
          if (l<=0) break;
          v.addAll(v.sz, buf, 0, l);
        }
        inflated = v.get(0, v.sz);
      } catch (IllegalArgumentException | DataFormatException e) { Log.stacktrace("paste decode", e); break paste; }
      
      openText(new String(inflated, StandardCharsets.UTF_8), m.gc.langs().fromName(lang));
      return;
    }
    
    // uploaded text file
    if (info.type==LinkType.TEXT) {
      int action = popupCounter.next();
      Runnable done = m.doAction("loading text file...");
      queueGet(uri).then(bs -> {
        done.run();
        if (popupCounter.superseded(action)) return;
        if (bs == null) m.gc.openLink(uri);
        else openText(new String(bs, StandardCharsets.UTF_8), m.gc.langs().defLang);
      });
      return;
    }
    
    openURIGeneric(uri, info);
  }
  private void openText(String text, Lang lang) {
    new Popup(m.ctx.win()) {
      protected void unfocused() { if (isVW) close(); }
      protected Rect fullRect() { return centered(m.ctx.vw(), 0.8, 0.8); }
      protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a); }
      
      protected void setup() {
        CodeAreaNode e = (CodeAreaNode) node.ctx.id("src");
        e.append(text);
        if (lang!=null) e.setLang(lang);
        e.um.clear();
        e.focusMe();
      }
    }.openVW(m.gc, m.ctx, m.gc.getProp("chat.textUI").gr(), true);
  }
  
  public static final HashMap<String, String> pasteMap = new HashMap<>();
  static {
    pasteMap.put("APL", "APL");
    pasteMap.put("APL18", "APL");
    pasteMap.put("dAPL", "APL");
    pasteMap.put("dAPL18", "APL");
    pasteMap.put("BQN", "BQN");
    pasteMap.put("BQN18", "BQN");
    pasteMap.put("C", "C");
    pasteMap.put("Java", "Java");
    pasteMap.put("singeli", "Singeli");
    // pasteMap.put("JS", "");
    pasteMap.put("asm", "x86 assembly");
    // pasteMap.put("k", "");
    // pasteMap.put("py", "");
    // pasteMap.put("svg", "");
  }
}
