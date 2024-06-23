package chat;

import chat.ui.ChatTextFieldNode;
import chat.utils.UnreadInfo;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.*;
import dzaima.ui.node.types.*;
import dzaima.utils.*;

import java.text.DecimalFormat;
import java.time.*;

public abstract class MuteState {
  public static final MuteState UNMUTED = new MuteState(null){
    protected UnreadInfo ownedInfo() { throw new IllegalStateException(); }
    protected void updated() { throw new IllegalStateException(); }
  };
  public boolean muted;
  public boolean mutePings;
  public Instant unmuteTime;
  public final ChatMain m;
  
  public MuteState(ChatMain m) { this.m = m; }
  
  public String serialize() {
    if (!nonDefault()) return "";
    return (muted?'1':'0')+" "+(mutePings?'1':'0')+" "+(unmuteTime==null?"i":unmuteTime.toEpochMilli());
  }
  public void deserialize(String s) {
    if (s.isEmpty()) { copyFrom(UNMUTED); return; }
    String[] ps = Tools.split(s, ' ');
    muted = ps[0].equals("1");
    mutePings = ps[1].equals("1");
    unmuteTime = ps[2].equals("i")? null : Instant.ofEpochMilli(Long.parseLong(ps[2]));
    updated();
  }
  
  public void copyFrom(MuteState s) {
    muted = s.muted;
    mutePings = s.mutePings;
    unmuteTime = s.unmuteTime;
    updated();
  }
  protected abstract UnreadInfo ownedInfo();
  protected abstract void updated();
  
  public void tick() {
    if (unmuteTime!=null && Instant.now().isAfter(unmuteTime)) copyFrom(UNMUTED);
  }
  
  
  public boolean nonDefault() {
    return muted || mutePings;
  }
  public boolean countUnreads() {
    return !muted;
  }
  public boolean countPings() {
    return !muted && !mutePings;
  }
  
  // TODO merge, returning a pair
  public UnreadInfo info() {
    boolean p = countPings();
    boolean u = countUnreads();
    if (p && u) return ownedInfo();
    if (!p && !u) return UnreadInfo.NONE;
    UnreadInfo i = ownedInfo();
    return new UnreadInfo(u? i.unread : 0, p? i.ping : false);
  }
  
  public boolean hidden(UnreadInfo u) {
    if (u.ping && countPings()) return false;
    if (u.unread!=0 && countUnreads()) return false;
    return muted;
  }
  
  private static String fmtd(double d, String t) {
    String s = new DecimalFormat("0.##").format(d);
    return s.equals("1")? "1 "+t : s+" "+t+"s";
  }
  public static String durationToString(Duration d) {
    if (d==null) return "∞";
    double c = d.toNanos()/1e9;
    if (c < 60) return fmtd(c, "second");
    c/= 60;
    if (c < 60) return fmtd(c, "minute");
    c/= 60;
    if (c==24) return "1 day";
    if (c < 48) return fmtd(c, "hour");
    c/= 24;
    return fmtd(c, "day");
  }
  public static Duration stringToDuration(String s) {
    int n = 0;
    while (n<s.length() && "0123456789.e".indexOf(s.charAt(n))!=-1) n++;
    double f;
    if (n==0) {
      f = 1;
    } else {
      try { f = Double.parseDouble(s.substring(0, n)); }
      catch (Exception e) { return null; }
    }
    Duration d = null;
    if      (s.contains("sec")) d = Duration.ofNanos((long) (f*1e9));
    else if (s.contains("min")) d = Duration.ofSeconds((long) (f*60));
    else if (s.contains("hour")) d = Duration.ofMinutes((long) (f*60));
    else if (s.contains("day")) d = Duration.ofMinutes((long) (f*60*24));
    else if (s.endsWith("s")) d = Duration.ofNanos((long) (f*1e9));
    else if (s.endsWith("m")) d = Duration.ofSeconds((long) (f*60));
    else if (s.endsWith("h")) d = Duration.ofMinutes((long) (f*60));
    else if (s.endsWith("d")) d = Duration.ofMinutes((long) (f*60*24));
    return d;
  }
  
  public void openMenu() {
    new Popup(m) {
      protected Rect fullRect() { return centered(m.ctx.vw, 0, 0); }
      protected boolean key(Key key, KeyAction a) { return defaultKeys(key, a) || ChatMain.keyFocus(pw, key, a) || true; }
      protected void unfocused() { close(); }
      
      public void stopped() {
        String s = time.getAll();
        Duration d = stringToDuration(s);
        if (d==null) unmuteTime = null;
        else unmuteTime = Instant.now().plus(d);
        updated();
      }
      private void fixupTime() {
        String s = time.getAll();
        time.removeAll();
        Duration d = stringToDuration(s);
        time.append(d==null? "∞" : durationToString(d));
      }
      
      ChatTextFieldNode time;
      protected void setup() {
        time = (ChatTextFieldNode) node.ctx.id("time");
        time.setFn((a, mod) -> { fixupTime(); return true; });
        time.onUnfocus = this::fixupTime;
        time.append(unmuteTime==null? "∞" : durationToString(Duration.between(Instant.now(), unmuteTime)));
        unmuteTime = null;
        
        for (String c : new String[]{"m15", "m30", "h1", "h3", "h24", "inf"}) {
          ((BtnNode) node.ctx.id(c)).setFn(b -> {
            time.removeAll();
            time.append(c.equals("inf")? "∞" : c.substring(1) + c.charAt(0));
            fixupTime();
            store();
          });
        }
        CheckboxNode muteE = (CheckboxNode) node.ctx.id("mute");
        CheckboxNode muteP = (CheckboxNode) node.ctx.id("mutePings");
        
        muteE.set(muted);
        muteP.set(mutePings);
        muteE.setFn(b -> { muted=b; store(); });
        muteP.setFn(b -> { mutePings=b; store(); });
        store();
      }
      private void store() {
        updated();
      }
    }.open(m.gc, m.ctx, m.gc.getProp("chat.roomMenu.muteUI").gr());
  }
  
  public void addMenuOptions(PartialMenu pm) {
    if (nonDefault()) {
      pm.add(pm.gc.getProp("chat.roomMenu.unmute").gr(), (s) -> {
        switch (s) {
          case "unmute": copyFrom(UNMUTED); return true;
          case "muteEdit": openMenu(); return true;
        }
        return false;
      });
    } else {
      pm.add(pm.gc.getProp("chat.roomMenu.mute").gr(), "mute", () -> { copyFrom(m.defaultMuteState); openMenu(); });
    }
  }
}
