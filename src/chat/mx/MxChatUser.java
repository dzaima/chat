package chat.mx;

import chat.*;
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
import java.util.function.*;
import java.util.zip.*;

import static chat.mx.MxChatroom.DEFAULT_MSGS;

public class MxChatUser extends ChatUser {
  public final Obj data;
  public MxServer s = null;
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
    });
    Utils.requestLogger.got(rq, "result", res);
    return res;
  }
  
  public String upload(byte[] data, String name, String mime) {
    String location = "/_matrix/media/r0/upload?filename="+Utils.toURI(name)+"&access_token="+s.gToken;
    String req = s.url + location;
    NetworkLog.CustomRequest rq = new NetworkLog.CustomRequest(Utils.RequestType.POST, location);
    Utils.requestLogger.got(rq, "new", s);
    String res = Utils.postPut("POST", req, data, mime);
    Utils.requestLogger.got(rq, "result", res);
    Obj o = JSON.parseObj(res);
    return o.str("content_uri");
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
  
  public MxChatUser(ChatMain m, Obj dataIn) {
    super(m);
    this.data = dataIn;
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
    node.ctx.id("server").replace(0, new StringNode(node.ctx, login.getServer().replaceFirst("^https?://", "")));
    queueNetwork(() -> {
      MxServer s0 = MxServer.of(login);
      if (s0==null) {
        Log.error("mx", "Failed to log in");
        return;
      }
      MxLogin u0 = s0.primaryLogin;
      
      String name = u0.user().globalName();
      primary.add(() -> {
        s = s0;
        u = u0;
        node.ctx.id("name").replace(0, new StringNode(node.ctx, name));
      });
      
      Obj j = u0.s.requestV3("sync").prop("filter", MxServer.syncFilter(DEFAULT_MSGS, lazyLoadUsers, true).toString()).token(u0.token).get().runJ();
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
  
  
  public Promise<byte[]> queueGet(String msg, String url) {
    return Promise.create(res -> queueRequest(() -> MxChatUser.rawCachedGet(m, msg, url), res::set));
  }
  public void loadImg(String url, Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, Supplier<Boolean> stillNeeded) {
    loadImg(url, url, loaded, ctor, stillNeeded);
  }
  public void loadImg(String url, String link, Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, Supplier<Boolean> stillNeeded) { // TODO pass actually useful stillNeeded
    media.request(url, d -> primary.add(() -> loaded.accept(HTMLParser.inlineImage(this, link, url.equals(link), d, ctor))), stillNeeded);
  }
  public void loadMxcImg(String mxc, Consumer<Node> loaded, BiFunction<Ctx, byte[], ImageNode> ctor, int w, int h, MxServer.ThumbnailMode mode, Supplier<Boolean> stillNeeded) {
    loadImg(s.mxcToThumbnailURL(mxc, w, h, mode), s.mxcToURL(mxc), loaded, ctor, stillNeeded);
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
  
  private void openLinkGeneric(String url) {
    m.gc.openLink(url);
  }
  public static final Counter popupCounter = new Counter();
  public void openLink(String url, LinkType type, byte[] data) {
    if (type==LinkType.EXT) {
      openLinkGeneric(url);
      return;
    }
    
    // known event/room
    if (url.startsWith("https://matrix.to/#/")) {
      try {
        URI u = new URI(url);
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
              if (!b) openLinkGeneric(url);
            }, false);
            return;
          }
        }
      } catch (URISyntaxException ignored) { }
    }
    
    // displayable image/animation
    if (m.gc.getProp("chat.internalImageViewer").b() && (type==LinkType.IMG || url.contains("/_matrix/media/"))) {
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
        openLinkGeneric(url);
      };
      
      if (data!=null) {
        showImg.accept(data);
        return;
      }
      
      Runnable done = m.doAction("loading image...");
      queueRequest(() -> CacheObj.compute("head_isImage\0"+url, () -> {
        try {
          Utils.log("head", url);
          HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
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
      }), isImg -> {
        if (isImg!=null && isImg[0]==1) {
          queueGet("image", url).then(d -> {
            done.run();
            showImg.accept(d);
          });
        } else {
          done.run();
          openLinkGeneric(url);
        }
      });
      
      return;
    }
    
    // https://dzaima.github.io/paste
    Pair<String, String> parts = HTMLParser.urlFrag(HTMLParser.fixURL(url));
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
    if (type==LinkType.TEXT) {
      int action = popupCounter.next();
      Runnable done = m.doAction("loading text file...");
      queueGet("Load text", url).then(bs -> {
        done.run();
        if (popupCounter.superseded(action)) return;
        if (bs == null) m.gc.openLink(url);
        else openText(new String(bs, StandardCharsets.UTF_8), m.gc.langs().defLang);
      });
    }
    
    openLinkGeneric(url);
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
    // pasteMap.put("asm", "");
    // pasteMap.put("k", "");
    // pasteMap.put("py", "");
    // pasteMap.put("svg", "");
  }
}
