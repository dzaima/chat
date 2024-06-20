package chat.mx;

import chat.*;
import chat.networkLog.*;
import dzaima.ui.gui.Popup;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.Node;
import dzaima.ui.node.types.BtnNode;
import dzaima.ui.node.types.editable.*;
import dzaima.utils.*;
import io.github.humbleui.skija.Image;
import libMx.*;

import java.io.IOException;
import java.nio.file.*;

public class MxLiveView extends LiveView {
  public final MxChatroom r;
  public final MxLog log;
  
  public MxLiveView(MxChatroom r, MxLog log) {
    super(r.m);
    this.r = r;
    this.log = log;
    initCompete();
  }
  
  public Chatroom room() { return r; }
  
  public MuteState muteState() {
    return r.muteState;
  }
  
  public Pair<Integer, Boolean> unreadInfo() {
    return log.unreadInfo();
  }
  
  public void show() { log.show(); super.show(); }
  public void hide() { super.hide(); log.hide(); }
  
  public Node inputPlaceContent() {
    if (r.myStatus==MxChatroom.MyStatus.INVITED) {
      Node n = m.ctx.make(m.gc.getProp("chat.inviteOptions").gr());
      ((BtnNode) n.ctx.id("accept")).setFn(b -> r.r.selfJoin());
      ((BtnNode) n.ctx.id("deny")).setFn(b -> r.r.selfRejectInvite());
      return n;
    }
    return input;
  }
  
  public void mentionUser(String id) {
    input.um.pushL("tag user");
    input.pasteText(id+" ");
    input.um.pop();
  }
  
  public String title() { return this==r.mainLiveView? r.title() : r.title() + " → thread"; }
  
  public boolean navigationKey(Key key, KeyAction a) { return false; }
  public boolean actionKey(Key key, KeyAction a) {
    return m.onCancel(key, a, () -> {
      if (!r.mainView().open) m.toRoom(r.mainView());
    }) || super.actionKey(key, a);
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
    return r.asCodeblock(s);
  }
  
  public boolean contains(ChatEvent ev) {
    return log.contains(ev);
  }
  
  public View getSearch() {
    return new MxSearchView(r.m, this);
  }
  
  private String lastReadTo;
  public void markAsRead() {
    // mark all events in this log as read in all logs they're in
    Pair<Integer, Boolean> u = unreadInfo();
    if (u.a!=0 || u.b) {
      for (MxChatEvent e : Vec.ofCollection(r.unreads.getForA(log))) r.unreads.removeAllB(e);
      for (MxChatEvent e : Vec.ofCollection(r.pings.getForA(log))) r.pings.removeAllB(e);
      r.unreadChanged();
    }
    if (log.list.size()<=1) return;
    MxEvent last = log.lastEvent;
    if (last==null || last.id.equals(lastReadTo)) return;
    lastReadTo = last.id;
    if (!last.uid.equals(r.u.id())) {
      r.u.queueNetwork(() -> r.r.readTo(last.id, log.threadID));
    }
  }
  
  public ChatEvent prevMsg(ChatEvent msg, boolean mine) { return log.prevMsg(msg, mine); }
  public ChatEvent nextMsg(ChatEvent msg, boolean mine) { return log.nextMsg(msg, mine); }
  
  public void older() {
    if (this == r.mainLiveView) r.older(); // TODO thread (if that's even possible)
  }
  
  
  
  public String upload(byte[] data, String name, String mime) {
    String location = "/_matrix/media/r0/upload?filename="+ Utils.toURI(name)+"&access_token="+r.r.s.gToken;
    String req = r.r.s.url + location;
    NetworkLog.CustomRequest rq = new NetworkLog.CustomRequest(Utils.RequestType.POST, location);
    Utils.requestLogger.got(rq, "new", r.r.s);
    String res = Utils.postPut("POST", req, data, mime);
    Utils.requestLogger.got(rq, "result", res);
    JSON.Obj o = JSON.parseObj(res);
    return o.str("content_uri");
  }
  
  public void post(String raw, String replyTo) {
    MxFmt f;
    String[] cmd = r.command(raw);
    getF: {
      if (cmd.length == 2) {
        MxChatroom.MxCommand fn = r.commands.linearFind(c -> c.name.equals(cmd[0]));
        if (fn != null) {
          f = fn.process.apply(cmd[1]);
          if (f == null) return;
          break getF;
        }
      }
      f = r.parse(raw);
    }
    
    if (replyTo!=null) {
      MxChatEvent tce = r.allKnownEvents.get(replyTo);
      if (tce!=null) f.reply(r.r, replyTo, tce.e0.uid, tce.username);
      else f.replyTo(r.r, replyTo);
    }
    
    if (log.threadID!=null) f.inThread(log.threadID);
    
    r.u.queueNetwork(() -> r.r.s.primaryLogin.sendMessage(r.r, f));
  }
  
  public void edit(ChatEvent m, String raw) {
    MxFmt f = r.parse(raw);
    r.u.queueNetwork(() -> r.r.s.primaryLogin.editMessage(r.r, m.id, f));
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
          input.um.pushL("insert link");
          input.pasteText(r.u.s.mxcToURL(l));
          input.um.pop();
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
          r.u.queueNetwork(() -> r.r.s.primaryLogin.sendMessage(r.r, f));
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
}
