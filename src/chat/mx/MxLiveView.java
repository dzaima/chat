package chat.mx;

import chat.*;
import chat.utils.UnreadInfo;
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
  public MuteState muteState() { return r.muteState; }
  public UnreadInfo unreadInfo() { return log.unreadInfo(); }
  
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
    if (log.isThread() && m.onCancel(key, a, () -> {
      if (!r.mainView().open) m.toRoom(r.mainView());
    })) return true;
    
    return super.actionKey(key, a);
  }
  
  public boolean typed(int codepoint) {
    if (codepoint=='`' && input.anySel()) {
      input.um.pushL("backtick code");
      for (Cursor c : input.cs) {
        String s = input.getByCursor(c);
        c.clearSel();
        input.insert(c.sx, c.sy, r.asCodeblock(s));
      }
      input.um.pop();
      return true;
    }
    return false;
  }
  
  public boolean contains(ChatEvent ev) { return log.contains(ev); }
  public View getSearch() { return new MxSearchView(r.m, this); }
  
  private String lastReadTo;
  public void markAsRead() {
    // mark all events in this log as read in all logs they're in
    UnreadInfo u = unreadInfo();
    if (u.any()) {
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
    if (!log.isMain()) {
      if (log.get(log.threadID) != null) return;
      if (log.globalPaging) {
        prevBatch = r.mainLiveView.prevBatch;
        log.globalPaging = false;
      }
    }
    mxBaseOlder();
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
      MxChatEvent tce = r.editRootEvent(replyTo);
      if (tce!=null) f.reply(r.r, replyTo, tce.e0.uid, tce.senderDisplay());
      else f.replyTo(r.r, replyTo);
    }
    
    if (log.isThread()) f.inThread(log.threadID);
    
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
          if (log.isThread()) f.inThread(log.threadID);
          if (input.replying instanceof MxChatEvent) {
            f.replyTo(r.r, input.replying.id);
            input.markReply(null);
          }
          r.u.queueNetwork(() -> r.r.s.primaryLogin.sendMessage(r.r, f));
          close();
        });
      }
      byte[] data;
      String getUpload() {
        try {
          data = Files.readAllBytes(Paths.get(path.getAll()));
          String name1 = name.getAll();
          String mime1 = mime.getAll();
          return r.u.upload(data, name1, mime1);
        } catch (IOException e) {
          Log.stacktrace("mx upload", e);
          return null;
        }
      }
    }.open(m.gc, m.ctx, m.gc.getProp("chat.mxUpload").gr());
  }
  
  
  private boolean msgLogToStart = false;
  private long nextOlder;
  public String prevBatch;
  public void mxBaseOlder() {
    if (msgLogToStart || prevBatch==null) return;
    if (System.currentTimeMillis()<nextOlder) return;
    nextOlder = Long.MAX_VALUE;
    Log.fine("mx", "Loading older messages in "+r.prettyID()+"+"+log.prettyID());
    JSON.Obj filter = MxRoom.roomEventFilter(!r.hasFullUserList());
    r.u.queueRequest(() -> {
      int n = r.globalLog().size() < 50? 50 : 100;
      if (log.isMain()) return r.r.beforeTok(filter, prevBatch, n);
      else return r.r.relationsBeforeTok(log.threadID, null, prevBatch, n);
    }, chunk -> {
      nextOlder = System.currentTimeMillis()+500;
      if (chunk==null) { Log.warn("mx", "MxRoom::beforeTok failed on token "+ prevBatch); return; }
      r.loadQuestionableMemberState(chunk); // TODO this doesn't actually exist for relation paging :|
      if (chunk.events.isEmpty()) msgLogToStart = true;
      prevBatch = chunk.eTok;
      if (log.isMain()) {
        Vec<Vec<MxChatEvent>> allEvents = new Vec<>();
        for (Pair<MxLog, Vec<MxEvent>> p : Tools.group(Vec.ofCollection(chunk.events), r::primaryLogOf)) {
          if (p.a.globalPaging) allEvents.add(p.a.addEvents(p.b, false));
        }
        for (Vec<MxChatEvent> events : allEvents) for (MxChatEvent c : events) {
          r.maybeThreadRoot(c); // make sure to run this after all other potential events in thread are added
        }
      } else {
        log.addEvents(chunk.events, false);
        if (chunk.eTok==null) {
          r.u.queueRequest(() -> r.r.msgContext(filter, log.threadID, 0), root -> {
            if (root==null) return;
            r.loadQuestionableMemberState(root);
            for (MxChatEvent e : log.addEvents(root.events, false)) e.markHasThread();
          });
        }
      }
    });
  }
}
