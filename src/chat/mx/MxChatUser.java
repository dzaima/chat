package chat.mx;

import chat.*;
import chat.ui.*;
import chat.ui.Extras.LinkType;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.types.StringNode;
import dzaima.ui.node.types.editable.code.*;
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
  
  public MxSync2 sync;
  
  public HashMap<String, MxChatroom> roomMap = new HashMap<>();
  public Collection<MxChatroom> roomSet = roomMap.values();
  
  private final ConcurrentLinkedQueue<Runnable> primary = new ConcurrentLinkedQueue<>();
  private final LinkedBlockingDeque<Runnable> network = new LinkedBlockingDeque<>();
  
  public static void logGet(String logText, String url) {
    MxServer.log("file", logText+" "+url);
  }
  public static byte[] get(String logText, String url) {
    return CacheObj.compute(url, () -> {
      logGet(logText, url);
      try { return Tools.get(url); }
      catch (RuntimeException e) { Log.warn(logText, "Failed to load "+url); return null; }
    });
  }
  
  public void queueNetwork(Runnable r) { network.add(r); }
  @FunctionalInterface public interface Request<T> { T get() throws Throwable; }
  
  // calling again with the same counter will cancel the previous request if it wasn't already invoked on the main thread
  public <T> void queueRequest(Counter c, Request<T> network, Consumer<T> primary) {
    int v = c==null? 0 : ++c.value;
    queueNetwork(() -> {
      T r;
      try { r = network.get(); }
      catch (Throwable e) { Log.stacktrace("mx queueRequest", e); r = null; }
      T finalR = r;
      this.primary.add(() -> { if (c==null || v==c.value) primary.accept(finalR); });
    });
  }
  
  public Thread networkThread = Tools.thread(() -> {
    while (true) {
      try {
        network.take().run();
      } catch (InterruptedException e) {
        return;
      } catch (Throwable e) {
        Log.stacktrace("mx networkThread", e);
      }
    }
  });
  MediaThread media = new MediaThread();
  
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
    node.ctx.id("server").replace(0, new StringNode(node.ctx, login.getServer().replaceFirst("^https?://", "")));
    queueNetwork(() -> {
      MxServer s0 = MxServer.of(login);
      if (s0==null) {
        Log.error("mx", "Failed to log in");
        return;
      }
      MxLogin u0 = s0.primaryLogin;
      
      String name = u0.user().name();
      primary.add(() -> {
        s = s0;
        u = u0;
        node.ctx.id("name").replace(0, new StringNode(node.ctx, name));
      });
      
      Obj j = u0.s.getJ("_matrix/client/r0/sync?filter={\"room\":{\"timeline\":{\"limit\":" + DEFAULT_MSGS + "}}}&access_token=" + u0.token);
      primary.add(() -> {
        for (Entry e : j.obj("rooms", Obj.E).obj("join", Obj.E).entries()) {
          MxChatroom r = new MxChatroom(this, e.k, e.v.obj());
          roomMap.put(e.k, r);
        }
        
        Arr storedStructure = data.arr("roomStructure", Arr.E);
        restoreTree(storedStructure, data.arr("roomOrder", null));
        if (data.has("roomOrder")) data.remove("roomOrder");
        saveRooms();
      });
      
      MxSync2 sync0 = new MxSync2(s0, j.str("next_batch"));
      sync0.start();
      sync = sync0;
    });
  }
  
  public void restoreTree(JSON.Arr state, JSON.Arr legacyOrder) {
    RoomTree.restoreTree(this, state, legacyOrder);
  }
  
  
  public void saveRooms() {
    data.put("roomStructure", RoomTree.saveTree(roomListNode));
    m.requestSave();
  }
  
  public Vec<Chatroom> rooms() {
    Vec<Chatroom> r = new Vec<>();
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
      boolean newRooms = false;
      Obj m = sync.poll(); if (m==null) break;
      Obj crs = m.obj("rooms", Obj.E).obj("join", Obj.E);
      for (JSON.Entry k : crs.entries()) {
        MxChatroom room = roomMap.get(k.k);
        if (room==null) {
          MxChatroom r = new MxChatroom(this, k.k, k.v.obj());
          preRoomListChange();
          roomMap.put(k.k, r);
          roomListNode.add(r.node); // TODO place in space if appropriate
          newRooms = true;
        } else room.update(k.v.obj());
      }
      if (newRooms) roomListChanged();
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
  
  
  public void queueGet(String msg, String url, Consumer<byte[]> loaded) {
    queueRequest(null, () -> MxChatUser.get(msg, url), loaded);
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
  public void openLink(String url, LinkType type, byte[] data) {
    if (type!=LinkType.EXT) {
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
              m.toRoom(r);
              return;
            }
          }
          if (n==2) {
            MxChatroom r = findRoom(parts[0]);
            String msgId = parts[1];
            if (r!=null) {
              MxChatEvent ev = r.log.get(msgId);
              if (ev!=null) {
                m.toRoom(r, ev);
                return;
              }
              r.openTranscript(msgId, v -> {
                if (!v) m.gc.openLink(url);
              }, false);
              return;
            }
          }
        } catch (URISyntaxException ignored) { }
      }
      
      if (m.gc.getProp("chat.internalImageViewer").b() && (type==LinkType.IMG || url.contains("/_matrix/media/"))) {
        byte[] d = data;
        if (d==null) {
          try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("HEAD");
            String[] ct = new String[1];
            c.getHeaderFields().forEach((k, v) -> {
              if ("Content-Type".equals(k) && v.size()==1) ct[0] = v.get(0);
            });
            c.disconnect();
            
            if (ct[0]!=null && ct[0].startsWith("image/")) {
              d = Tools.get(url);
            }
          } catch (Throwable e) {
            Log.stacktrace("mx image type", e);
          }
        }
        if (d!=null) {
          Animation anim = new Animation(d);
          if (anim.valid) {
            m.viewImage(anim);
            return;
          }
        }
      }
      
      paste: if (m.gc.getProp("chat.internalPasteViewer").b() && url.startsWith("https://dzaima.github.io/paste")) {
        String[] ps;
        try {
          ps = Tools.split(new URI(HTMLParser.fixURL(url)).getFragment(), '#');
        } catch (URISyntaxException e) { Log.stacktrace("paste URL", e); break paste; }
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
      
      if (type==LinkType.TEXT) {
        byte[] bs = get("Load text", url);
        if (bs!=null) {
          openText(new String(bs, StandardCharsets.UTF_8), m.gc.langs().defLang);
          return;
        }
      }
    }
    
    m.gc.openLink(url);
  }
  private void openText(String text, Language lang) {
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
