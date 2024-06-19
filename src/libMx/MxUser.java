package libMx;

public class MxUser {
  public final MxServer s;
  public final String uid;
  
  public MxUser(MxServer s, String uid) {
    this.s = s;
    this.uid = uid;
  }
  
  public String globalName() {
    return s.requestV3("profile",uid,"displayname").gToken().get().runJ().str("displayname");
  }
  
  public static final String nameChars = "abcdefghijklmnopqrstuvwxyz0123456789._=-/";
}
