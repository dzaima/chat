package chat;

import dzaima.utils.*;
import libMx.*;

import java.util.Arrays;
import java.util.function.Function;

public class MDParser {
  private static Function<String, String> toUsername;
  private int i;
  private String s;
  private boolean eof;
  public int[] styles;
  public String html;
  
  public static final int S_DEF = 0;
  public static final int S_CODE = 32;
  public static final int S_CODE_ESC = 33;
  public static final int S_LINK = 34;
  public static final int S_DEF_ESC = 35;
  public static final int S_QUOTE = 36;
  public static final int S_QUOTE_LEAD = 37;
  public static final int S_MASK = ~31;
  
  public static final int SD_I  = 1; // italics
  public static final int SD_B  = 2; // bold
  public static final int SD_ST = 4; // strikethrough
  public static final int SD_SP = 8; // spoiler
  
  public static final String ESCAPABLE = "!\"#$%&'()*+,-./:;<=>?@[]^_`{|}~\\";
  
  private void ss(int s, int e, int m) {
    Arrays.fill(styles, s, e, m);
  }
  private void as(int s, int e, int sd) {
    for (int j = s; j < e; j++) {
      if ((styles[j] & S_MASK) == S_DEF) styles[j]|= sd;
    }
  }
  
  public static MDParser eval(String s, Function<String, String> toUsername) {
    MDParser.toUsername = toUsername;
    MDParser g = new MDParser();
    g.s = s;
    g.styles = new int[s.length()];
    g.html = g.run('\0');
    return g;
  }
  public static String toHTML(String s, Function<String, String> toUsername) {
    return eval(s, toUsername).html;
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
      else if (c=='*' && ifTag(r, "*", '*', "b", SD_B)) { }
      else if (c=='-' && ifTag(r, "---", '-', "del", SD_ST)) { }
      else if (c=='~' && ifTag(r, "~~", '~', "del", SD_ST)) { }
      else if (c=='|' && ifTag(r, "||", '|', "span", " data-mx-spoiler", SD_SP)) { }
      else if (c=='_' && border(s, i-2) && i<s.length() && s.charAt(i)!=' ' && s.charAt(i)!='_' && ifTag(r, "_", '_', "i", SD_I)) { }
      else if (c=='\\' && i<s.length() && ESCAPABLE.indexOf(s.charAt(i))!=-1) {
        ss(i-1, i, S_DEF_ESC);
        addText(r, s.charAt(i++));
      }
      else if (c=='@' && border(s, i-2)) {
        int j = i;
        while (j<s.length() && (MxUser.nameChars.indexOf(c=s.charAt(j))!=-1 || c==':')) j++;
        if (s.charAt(j-1)==':') j--;
        String id = s.substring(i-1, j);
        String name = toUsername.apply(id);
        if (name==null) r.append('@');
        else {
          r.append("<a href=").append(htmlString("https://matrix.to/#/"+id)).append(">").append(Utils.toHTML(name, false)).append("</a>");
          int colon = s.indexOf(':', i);
          ss(colon==-1? j : Math.min(j, colon), j, S_DEF_ESC);
          i = j;
        }
      }
      else if (c=='>' && (i==1 || s.charAt(i-2)=='\n') && i<s.length() && s.charAt(i)==' ') {
        i--;
        int j = i;
        boolean first = true;
        r.append("<blockquote>");
        while (i+1 < s.length() && s.charAt(i)=='>' && s.charAt(i+1)==' ') {
          ss(i, i+1, S_QUOTE_LEAD);
          if (first) first = false;
          else r.append("<br>");
          i+= 2;
          int i1 = i;
          r.append(run('\n'));
          if (i1==i || i>=s.length()) break;
          if (s.charAt(i)=='\n') i++;
        }
        while (j<i && j<s.length()) {
          if (styles[j]==S_DEF) styles[j] = S_QUOTE;
          j++;
        }
        r.append("</blockquote>");
      }
      else if (c=='[') {
        int li = i;
        String v = run(']');
        int ls = i+1;
        if (i<s.length() && s.charAt(i)=='(') {
          int le = s.indexOf(')', i);
          if (le!=-1) {
            ss(li-1, li, S_DEF_ESC);
            ss(ls-2, ls, S_DEF_ESC);
            ss(ls, le, S_LINK);
            ss(le, le+1, S_DEF_ESC);
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
          IntVec escapes = new IntVec();
          while (ei<s.length()) {
            c = s.charAt(ei++);
            if (c=='`') { // `abc`
              i = ei;
              r.append("<code>");
              r.append(code);
              ss(sei-1, sei, S_DEF_ESC);
              ss(sei, i-1, S_CODE);
              ss(i-1, i, S_DEF_ESC);
              for (int cx : escapes.get(0, escapes.sz)) {
                ss(cx, cx+1, S_CODE_ESC);
              }
              r.append("</code>");
              continue str;
            }
            if (c=='\\' && ei<s.length() && ESCAPABLE.indexOf(s.charAt(ei))!=-1) {
              escapes.add(ei-1);
              c = s.charAt(ei++);
            }
            addText(code, c);
          }
          r.append('`'); // `abc
        } else { // ``...
          i = sei;
          int backtickEndI = i;
          if (am>=3 && s.indexOf('\n',i)>=0 && s.indexOf('`', i) > s.indexOf('\n',i)) { // ```[no more backticks]\n[whatever]`
            int le = s.indexOf('\n',i);
            String lang = s.substring(i, le);
            int afterStart = i;
            i = le+1;
            int cend = (s+"\n").indexOf("\n"+Tools.repeat('`', am)+"\n", i);
            if (cend==-1) {
              i = backtickEndI;
              r.append(Tools.repeat('`', am));
              continue;
            }
            String cont = le+1<cend? s.substring(le+1, cend) : "";
            i = cend+am+2;
            
            ss(afterStart-am, afterStart, S_DEF_ESC);
            ss(le, i-4, S_CODE);
            ss(i-1-am, i-1, S_DEF_ESC);
            r.append("<pre><code");
            if (lang.length()!=0) r.append(" class=\"language-").append(lang).append('\"');
            r.append('>');
            r.append(libMx.Utils.toHTML(cont, false));
            r.append("</code></pre>");
            if (i-1<s.length() && s.charAt(i-1)=='\n' && end=='\n') return r.toString();
          } else { // `` code with `backticks` ``
            int cend = s.indexOf(Tools.repeat('`', am), i);
            if (cend==-1) {
              r.append(Tools.repeat('`', am));
              continue;
            }
            ss(sei-am, sei, S_DEF_ESC);
            ss(cend, cend+am, S_DEF_ESC);
            String cont = s.substring(i, cend);
            int iOff = i;
            i = cend+am;
            int cl = cont.length();
            int cs=0 ; while (cs<cl && Character.isWhitespace(cont.charAt(cs  ))) cs++;
            int ce=cl; while (ce>0  && Character.isWhitespace(cont.charAt(ce-1))) ce--;
            cont = cs<ce? cont.substring(cs, ce) : "";
            if (cs<ce) ss(iOff+cs, iOff+ce, S_CODE);
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
  
  private boolean ifTag(StringBuilder b, String input, char end, String tag, int style) { return ifTag(b, input, end, tag, "", style); }
  private boolean ifTag(StringBuilder b, String input, char end, String tag, String attrib, int style) {
    int l = input.length();
    if (l > 1) {
      if (!s.startsWith(input, i-1)) return false;
      i+= l-1;
    }
    int li=i;
    String ct = run(end);
    if (eof) {
      b.append(input);
      b.append(ct);
    } else {
      as(li-l, li, S_DEF_ESC);
      as(li, i-l, style);
      as(i-l, i, S_DEF_ESC);
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
