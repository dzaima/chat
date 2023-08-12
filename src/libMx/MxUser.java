package libMx;

public class MxUser {
  public MxServer s;
  public String uid;
  
  public MxUser(MxServer s, String uid) {
    this.s = s;
    this.uid = uid;
  }
  
  private String name;
  public String name() {
    if (name == null) name = s.requestV3("profile",uid,"displayname").gToken().get().runJ().str("displayname");
    return name;
  }
  
  public static final String nameChars = "abcdefghijklmnopqrstuvwxyz0123456789._=-/";
}
