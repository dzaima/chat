package chat.mx;

import chat.*;
import dzaima.utils.*;

import java.time.*;
import java.util.*;

public class MxSearchView extends SearchView {
  public MxSearchView(ChatMain m, View originalView) {
    super(m, originalView);
  }
  
  public void hide() {
    hidePrev();
    super.hide();
  }
  
  public boolean contains(ChatEvent ev) {
    return false;
  }
  
  public void hidePrev() {
    for (MxChatEvent e : prevShown) e.hide();
    prevShown.clear();
  }
  
  private boolean tooFar(Instant a, Instant b) {
    return Duration.between(a, b).abs().getSeconds() > 60*60;
  }
  
  private static class Search {
    private final Vec<String> words = new Vec<>();
    private final boolean caseSensitive;
    private final String userSearch;
    public Search(String text, String userSearch, boolean caseSensitive, boolean exactMatch) {
      this.caseSensitive = caseSensitive;
      this.userSearch = userSearch.toLowerCase(Locale.ROOT);
      if (exactMatch) words.add(text); 
      else words.addAll(0, Tools.split(text, ' '));
      if (!caseSensitive) for (int i = 0; i < words.sz; i++) words.set(i, words.get(i).toLowerCase(Locale.ROOT));
    }
    public boolean matches(String body) {
      if (!caseSensitive) body = body.toLowerCase(Locale.ROOT);
      for (String c : words) if (!body.contains(c)) return false;
      return true;
    }
    public boolean matchesUser(String username) {
      return userSearch.isEmpty() || username.toLowerCase(Locale.ROOT).contains(userSearch);
    }
    public boolean triviallyAll() {
      boolean noTextSearch = words.sz==1 && words.get(0).isEmpty();
      boolean noUserSearch = userSearch.isEmpty();
      return noTextSearch && noUserSearch;
    }
  }
  
  public final Vec<MxChatEvent> prevShown = new Vec<>();
  public void processSearch(String text, String user) {
    m.removeAllMessages();
    hidePrev();
    Search s = new Search(text, user, caseSensitive(), exactMatch());
    if (s.triviallyAll()) return;
    
    boolean needCtx = showContext();
    int ctxSz = contextSize();
    
    Vec<MxLog> logs = new Vec<>();
    
    if (allRooms()) {
      for (ChatUser u : m.users) {
        if (u instanceof MxChatUser) {
          for (MxChatroom r : ((MxChatUser) u).roomSet) {
            logs.addAll(Vec.ofCollection(r.liveLogs.values()));
          }
        }
      }
    } else {
      MxLog log;
      if (originalView instanceof MxTranscriptView) log = ((MxTranscriptView) originalView).log;
      else if (originalView instanceof MxLiveView) {
        log = ((MxLiveView) originalView).log;
        if (log == log.r.mainLiveView.log) logs.addAll(Vec.ofCollection(log.r.liveLogs.values()).filter(c -> c!=log));
      }
      else { Log.error("mx", "bad MxSearchView original view"); return; }
      logs.add(log);
    }
    
    Vec<Vec<Pair<Boolean, MxChatEvent>>> groups = new Vec<>();
    HashSet<MxChatEvent> alreadyShown = new HashSet<>();
    for (MxLog l : logs) {
      Vec<MxChatEvent> ms = l.list.filter(c -> !(c instanceof MxChatNotice) && alreadyShown.add(c));
      Vec<Integer> matches = new Vec<>();
      for (int i = 0; i < ms.sz; i++) {
        MxChatEvent e = ms.get(i);
        if ((s.matchesUser(e.senderDisplay()) || s.matchesUser(e.senderID())) && (s.matches(e.body) || s.matches(e.src))) matches.add(i);
      }
      if (matches.sz==0) continue;
      if (needCtx) {
        HashMap<MxChatEvent, Integer> prev = new HashMap<>(); 
        for (Integer m : matches) {
          Instant exp = ms.get(m).time;
          Vec<Pair<Boolean, MxChatEvent>> cGroup = new Vec<>();
          for (int i = Math.max(0, m-ctxSz); i <= Math.min(m+ctxSz, ms.sz-1); i++) {
            MxChatEvent e = ms.get(i);
            if (tooFar(e.time, exp)) continue;
            alreadyShown.add(e);
            if (prev.containsKey(e)) {
              cGroup = groups.peek();
              if (m==i) cGroup.set(prev.get(e), new Pair<>(true, e));
            } else {
              prev.put(e, cGroup.size());
              cGroup.add(new Pair<>(m==i, e));
            }
          }
          if (cGroup!=groups.peek()) groups.add(cGroup);
        }
      } else {
        for (int c : matches) {
          MxChatEvent e = ms.get(c);
          alreadyShown.add(e);
          groups.add(Vec.of(new Pair<>(true, e)));
        }
      }
    }
    
    Vec<Pair<MxChatEvent, Vec<Pair<Boolean, MxChatEvent>>>> toSort = new Vec<>();
    for (Vec<Pair<Boolean, MxChatEvent>> c : groups) {
      MxChatEvent e0 = null;
      for (Pair<Boolean, MxChatEvent> p : c) if (p.a) { e0=p.b; break; }
      toSort.add(new Pair<>(e0, c));
    }
    
    toSort.sort(Comparator.comparing(c -> c.a.time));
    for (Pair<MxChatEvent, Vec<Pair<Boolean, MxChatEvent>>> c : toSort) {
      for (Pair<Boolean, MxChatEvent> p : c.b) {
        m.addMessage(p.b, false, true, !p.a);
        prevShown.add(p.b);
      }
    }
    m.msgsScroll.toYE(true);
  }
}
