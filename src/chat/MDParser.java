package chat;

import dzaima.utils.Tools;
import libMx.*;

import java.util.function.Function;

public class MDParser {
  private static Function<String, String> toUsername;
  private int i;
  private String s;
  private boolean eof;
  
  public static String toHTML(String s, Function<String, String> toUsername) {
    MDParser.toUsername = toUsername;
    MDParser g = new MDParser();
    g.s = s;
    return g.run('\0');
  }
  @SuppressWarnings("StatementWithEmptyBody")
  private String run(char end) {
    StringBuilder r = new StringBuilder();
    str: while (i<s.length()) {
      char c = s.charAt(i++);
      end: if (c==end) {
        if (end=='_' && !border(s, i)) break end;
        if (end=='-') { if (s.startsWith("--", i)) i+= 2; else break end; }
        if (end=='~') { if (s.startsWith("~",  i)) i++;   else break end; }
        if (end=='|') { if (s.startsWith("|",  i)) i++;   else break end; }
        return r.toString();
      }
      if (c=='\n') r.append("<br>");
      else if (c=='\\' && i<s.length() && "!\"#$%&'()*+,-./:;<=>?@[]^_`{|}~\\".indexOf(s.charAt(i))!=-1) addText(r, s.charAt(i++));
      else if (c=='*' && ifTag(r, "*", '*', "b")) { }
      else if (c=='-' && ifTag(r, "---", '-', "del")) { }
      else if (c=='~' && ifTag(r, "~~", '~', "del")) { }
      else if (c=='|' && ifTag(r, "||", '|', "span", " data-mx-spoiler")) { }
      else if (c=='_' && border(s, i-2) && ifTag(r, "_", '_', "i")) { }
      else if (c=='@' && border(s, i-2)) {
        int j = i;
        while (j<s.length() && (MxUser.nameChars.indexOf(c=s.charAt(j))!=-1 || c==':')) j++;
        if (s.charAt(j-1)==':') j--;
        String id = s.substring(i-1, j);
        String name = toUsername.apply(id);
        if (name==null) r.append('@');
        else {
          r.append("<a href=").append(htmlString("https://matrix.to/#/"+id)).append(">").append(Utils.toHTML(name, false)).append("</a>");
          i = j;
        }
      }
      else if (c=='[') {
        String v = run(']');
        int ls = i+1;
        if (i<s.length() && s.charAt(i)=='(') {
          int le = s.indexOf(')', i);
          if (le!=-1) {
            r.append("<a href=").append(htmlString(s.substring(ls, le))).append(">");
            r.append(v);
            r.append("</a>");
            i = le + 1;
            continue;
          }
        }
        r.append('[').append(v);
        if (!eof) r.append(']');
      }
      else if (c=='`') {
        int sei = i;
        while (sei<s.length() && s.charAt(sei)=='`') sei++;
        int am = sei-i+1;
        if (am==1) { // `abc...
          int ei = i;
          StringBuilder code = new StringBuilder();
          while (ei<s.length()) {
            c = s.charAt(ei++);
            if (c=='`') { // `abc`
              i = ei;
              r.append("<code>");
              r.append(code);
              r.append("</code>");
              continue str;
            }
            if (c=='\\' && ei<s.length()) c = s.charAt(ei++);
            addText(code, c);
          }
          r.append('`'); // `abc
        } else { // ``...
          i = sei;
          if (am==3 && s.indexOf('\n',i)>=0 && s.indexOf('`', i) > s.indexOf('\n',i)) { // ```[no more backticks]\n[whatever]`
            int le = s.indexOf('\n',i);
            String lang = s.substring(i, le);
            i = le+1;
            int cend = (s+"\n").indexOf("\n"+Tools.repeat('`', am)+"\n", i);
            if (cend==-1) cend = s.length();
            String cont = le+1<cend? s.substring(le+1, cend) : "";
            i = cend+am+2;
            r.append("<pre><code");
            if (lang.length()!=0) r.append(" class=\"language-").append(lang).append('\"');
            r.append('>');
            r.append(libMx.Utils.toHTML(cont, false));
            r.append("</code></pre>");
          } else { // `` code with `backticks` ``
            int cend = s.indexOf(Tools.repeat('`', am), i); if (cend==-1) cend = s.length();
            String cont = s.substring(i, cend);
            i = cend+am;
            int cl = cont.length();
            int cs=0 ; while (cs<cl && Character.isWhitespace(cont.charAt(cs  ))) cs++;
            int ce=cl; while (ce>0  && Character.isWhitespace(cont.charAt(ce-1))) ce--;
            cont = cs<ce? cont.substring(cs, ce) : "";
            r.append("<code>").append(libMx.Utils.toHTML(cont, false)).append("</code>");
          }
        }
      }
      else addText(r, c);
    }
    eof = true;
    return r.toString();
  }
  private void addText(StringBuilder b, char c) {
    if (c=='<') b.append("&lt;");
    else if (c=='>') b.append("&gt;");
    else if (c=='&') b.append("&amp;");
    else b.append(c);
  }
  private String htmlString(String s) {
    return "\""+s.replace("&", "&amp;").replace("\"", "&quot;")+"\"";
  }
  
  private boolean ifTag(StringBuilder b, String input, char end, String tag) { return ifTag(b, input, end, tag, ""); }
  private boolean ifTag(StringBuilder b, String input, char end, String tag, String attrib) {
    if (input.length() > 1) {
      if (!s.startsWith(input, i-1)) return false;
      i+= input.length()-1;
    }
    String ct = run(end);
    if (eof) {
      b.append(input);
      b.append(ct);
    } else {
      b.append("<").append(tag).append(attrib).append(">");
      b.append(ct);
      b.append("</").append(tag).append(">");
    }
    return true;
  }
  public static boolean border(String s, int i) {
    return i<0 || i>=s.length() || !Character.isLetterOrDigit(s.charAt(i));
  }
}
