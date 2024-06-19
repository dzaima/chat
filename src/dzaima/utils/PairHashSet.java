package dzaima.utils;

import java.util.*;

public class PairHashSet<A, B> {
  private final HashMap<A, HashSet<B>> keyA = new HashMap<>();
  private final HashMap<B, HashSet<A>> keyB = new HashMap<>();
  
  public boolean add(A a, B b) {
    boolean addA = keyA.computeIfAbsent(a, unused -> new HashSet<>()).add(b);
    if (!addA) return false;
    boolean addB = keyB.computeIfAbsent(b, unused -> new HashSet<>()).add(a);
    assert addB;
    return true;
  }
  
  public Collection<B> getForA(A a) { HashSet<B> s = keyA.get(a); return s==null? Collections.emptySet() : s; }
  public Collection<A> getForB(B b) { HashSet<A> s = keyB.get(b); return s==null? Collections.emptySet() : s; }
  
  // arg to make sure you're checking the correct type!
  public int uniqueA(Class<A> ignored) { return keyA.size(); }
  public int uniqueB(Class<B> ignored) { return keyB.size(); }
  
  public void removeAllA(A a) { HashSet<B> bs = keyA.remove(a); if (bs!=null) for (B b : bs) PairHashSetA.removeClear(keyB, b, a); }
  public void removeAllB(B b) { HashSet<A> as = keyB.remove(b); if (as!=null) for (A a : as) PairHashSetA.removeClear(keyA, a, b); }
  
  public boolean isEmpty() {
    return keyA.isEmpty();
  }
  
  public void clear() {
    keyA.clear();
    keyB.clear();
  }
}
