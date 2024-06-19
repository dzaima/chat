package chat.mx;

public class Counter {
  public int value;
  
  public int next() {
    return ++value;
  }
  
  public boolean superseded(int action) {
    return value != action;
  }
}
