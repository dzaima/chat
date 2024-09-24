package libMx;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public abstract class MxLoginMgr {
  public abstract String getServer();
  public abstract String getUserID();
  public abstract String getPassword();
  
  public abstract String getToken(); // returns null if none known
  public abstract void updateToken(String token); // 
  
  public MxServer create() {
    return new MxServer(getServer());
  }
  public MxLogin login(MxServer s) { // s.primaryLogin becomes the return value
    s.loadVersionInfo();
    MxLogin l = login0(s);
    if (l==null) return null;
    s.setG(l);
    return l;
  }
  
  private MxLogin login0(MxServer s) {
    String token = getToken();
    if (token!=null) {
      MxLogin l = new MxLogin(s, getUserID(), getToken());
      if (l.valid()) return l;
    }
    
    MxLogin l = s.login(getUserID(), getPassword());
    if (l==null) return null;
    
    updateToken(l.token);
    return l;
  }
  
  
  public static class MxFileLogin extends MxLoginMgr {
    private final Path path;
    private final List<String> lns;
    
    /* file contents:
       1. https://example.org
       2. @username:example.org
       3. password
       4. (token or end of file)
    */
    public MxFileLogin(Path path) {
      this.path = path;
      try {
        lns = Files.readAllLines(path);
        if (lns.size()==3) lns.add(null);
        if (lns.size()!=4) throw new RuntimeException("Expected login info file to have 3 or 4 lines");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    public String getServer()   { return lns.get(0); }
    public String getUserID()   { return lns.get(1); }
    public String getPassword() { return lns.get(2); }
    public String getToken()    { return lns.get(3); }
    
    public void updateToken(String token) {
      lns.set(3, token);
      Utils.write(path, lns.get(0)+"\n"+lns.get(1)+"\n"+lns.get(2)+"\n"+token);
    }
  }
}
