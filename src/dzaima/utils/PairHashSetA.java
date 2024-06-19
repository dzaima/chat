package dzaima.utils;

import java.util.*;

public class PairHashSetA<A, B> { // aka HashMap<K, HashSet<V>> but doesn't store empty sets
  public final HashMap<A, HashSet<B>> map = new HashMap<>();
  
  public HashSet<B> getSetForA(A a) { // null if no matches
    return map.get(a);
  }
  
  public boolean has(A a, B b) {
    HashSet<B> bs = map.get(a);
    return bs!=null && bs.contains(b);
  }
  
  public void add(A a, B b) {
    map.computeIfAbsent(a, unused -> new HashSet<>()).add(b);
  }
  
  public void remove(A a, B b) {
    HashSet<B> bs = map.get(a);
    if (bs==null) return;
    bs.remove(b);
    if (bs.isEmpty()) map.remove(a);
  }
}
