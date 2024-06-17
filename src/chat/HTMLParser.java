package chat;

import chat.ui.*;
import chat.ui.Extras.LinkType;
import dzaima.ui.eval.PNodeGroup;
import dzaima.ui.gui.*;
import dzaima.ui.gui.io.Click;
import dzaima.ui.gui.select.StringifiableNode;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.*;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.net.*;
import java.util.function.BiFunction;
import java.util.regex.*;

public class HTMLParser {
  
  public static Node parse(Chatroom r, String s) {
    int l = s.length();
    int ss = 0; while (ss<l && Character.isWhitespace(s.charAt(ss  ))) ss++;
    int se = l; while (se>0 && Character.isWhitespace(s.charAt(se-1))) se--;
    if (ss>=se) return new TextNode(r.m.base.ctx, Props.none());
    s = s.substring(ss, se);
    s = s.replace("\u2028", "\n");
    Element b = Jsoup.parse(s).body();
    TextNode base = new TextNode(r.m.base.ctx, Props.none());
    rec(b, base, false, null, r);
    return base;
  }
  
  private static final Props WIDTH_MAX = Props.of("width", new EnumProp("max"));
  public static Node inlineImage(ChatUser u, String link, boolean dataIsFull, byte[] data, BiFunction<Ctx, byte[], ImageNode> ctor) {
    Ctx ctx = u.m.base.ctx;
    ImageNode img = ctor.apply(ctx, data);
    if (!img.loadableImage()) return null;
    TextNode l = Extras.textLink(u, link, LinkType.IMG, dataIsFull? data : null);
    InlineNode.TANode v = new InlineNode.TANode(ctx, WIDTH_MAX);
    v.add(img);
    l.add(v);
    return l;
  }
  
  static class QuoteNode extends PadNode implements InlineNode.Scannable {
    public QuoteNode(Node ch) {
      super(ch.ctx, ch.ctx.finishProps(ch.gc.getProp("chat.quote.padFields").gr(), null));
      add(ch);
    }
    
    public void bg(Graphics g, boolean full) {
      pbg(g, full);
      g.rect(0, 0, gc.em/4f, h, gc.getProp("chat.quote.line").col());
    }
    
    public Iterable<Node> scannableCh() {
      return ch;
    }
  }
  
  private static Node pre(Ctx ctx, String c0) {
    if (c0.endsWith("\n")) c0 = c0.substring(0, c0.length()-1);
    return ctx.makeKV(ctx.gc.getProp("chat.code.$blockP").gr(), "body", new StringNode(ctx, c0));
  }
  
  
  private static final Pattern linkRegex = Pattern.compile("\\bhttps?://");
  @SuppressWarnings("StringConcatenationInLoop") // prettier here, whatever
  private static void rec(Element el, TextNode p, boolean mono, String link, Chatroom r) {
    int sz = el.childNodeSize();
    for (int i = 0; i < sz; i++) {
      org.jsoup.nodes.Node cn = el.childNode(i);
      if (cn instanceof org.jsoup.nodes.TextNode) {
        org.jsoup.nodes.TextNode c = (org.jsoup.nodes.TextNode) cn;
        String s = mono? c.getWholeText() : c.text();
        if (!mono) {
          Matcher m = linkRegex.matcher(s);
          if (m.find()) {
            int pi = 0;
            do {
              int start = m.start();
              int end = m.end();
              if (start<pi) continue;
              int depth = 0;
              while (end<s.length()) {
                char ch = s.charAt(end); 
                if (Character.isWhitespace(ch)) break;
                if (ch=='(' | ch=='[') depth++;
                if (ch==')' | ch==']') if(depth-- == 0) break;
                end++;
              }
              while (".;:,'\"".indexOf(s.charAt(end-1)) != -1) end--;
              String url = s.substring(start, end);
              boolean eq = false;
              eq|= url.equals(link);
              url = fixURL(url);
              eq|= url.equals(link);
              if (eq || link==null) attemptLink: try {
                URI uri = new URI(url);
                if (pi != start) p.add(new StringNode(p.ctx, s.substring(pi, start)));
                Node inner = p;
                if (link==null) p.add(inner = link(r, url, LinkType.UNK));
                
                String txt;
                int L = 100;
                trunc: {
                  if (uri.getHost()==null || uri.getScheme()==null || uri.getPath()==null) {
                    txt = url;
                    break trunc;
                  }
                  txt = uri.getHost();
                  if (!uri.getScheme().equals("https")) txt = uri.getScheme()+"://"+txt;
                  String[] path = Tools.split(uri.getPath(), '/');
                  int j = path[0].isEmpty()? 1 : 0;
                  while (j<path.length) {
                    String n = txt+"/"+path[j];
                    if (n.length()>L) { txt+="/…"; break trunc; }
                    txt = n; j++;
                  }
                  String q = uri.getQuery();
                  if (q!=null) {
                    String n = txt+"?"+q;
                    if (n.length()>L) { txt+="?…"; break trunc; }
                    txt = n;
                  }
                  String f = uri.getFragment();
                  if (f!=null) {
                    String n = txt+"#"+f;
                    if (n.length()>L) { txt+="#…"; break trunc; }
                    txt = n;
                  }
                }
                
                inner.add(new StringNode(p.ctx, txt));
                pi = end;
              } catch (URISyntaxException ignored) { /*bad links get bad formatting*/ }
            } while (m.find());
            if (pi!=s.length()) p.add(new StringNode(p.ctx, s.substring(pi)));
            continue;
          }
        }
        p.add(new StringNode(p.ctx, s));
      } else if (cn instanceof Element) {
        Element c = (Element) cn;
        String tag = c.tagName();
        switch (tag) {
          case "br":
          case "hr":
            p.add(new StringNode(p.ctx, "\n"));
            break;
          case "a":
            String url = fixURL(c.attr("href"));
            Pair<String,String> parts = urlFrag(url);
            if (parts.b!=null && parts.a.equals("https://matrix.to") && parts.b.startsWith("/@")) {
              String id = parts.b.substring(1);
              pill(r, p, c.text(), id, id.equals(r.user().id()));
            } else {
              TextNode t = link(r, url, LinkType.UNK);
              rec(c, t, mono, url, r);
              p.add(t);
            }
            break;
          case "img":
            if (c.hasAttr("src")) {
              Chatroom.URLRes src = r.parseURL(c.attr("src"));
              TextNode l = link(r, src.url, LinkType.IMG);
              l.add(new StringNode(p.ctx, c.hasAttr("alt")? c.attr("alt")
                                        : c.hasAttr("title")? c.attr("title")
                                        : src.safe? "(image loading)" : "image"));
              if (src.safe) r.user().loadImg(src.url, img -> {
                if (img==null) return; // TODO link fallback
                l.clearCh();
                l.add(img);
              }, c.hasAttr("data-mx-emoticon")? ImageNode.EmojiImageNode::new : ImageNode.InlineImageNode::new, () -> true);
              p.add(l);
            } else {
              p.add(new StringNode(p.ctx, "(<img> without URL)"));
            }
            break;
          case "code":
            wrap(p, c, true, link, r, "chat.code.inlineP");
            break;
          case "i": case "em":
            wrap(p, c, mono, link, r, I_PROP);
            break;
          case "strike": case "del":
            wrap(p, c, mono, link, r, S_PROP);
            break;
          case "b": case "strong":
            wrap(p, c, mono, link, r, B_PROP);
            break;
          case "u":
            wrap(p, c, mono, link, r, U_PROP);
            break;
          case "pre":
            Node n2 = pre(p.ctx, c.wholeText());
            p.add(new InlineNode.FullBlock(n2));
            break;
          case "span": case "font": {
            TextNode p1 = p;
            if (c.hasAttr("data-mx-spoiler")) p1 = wrap(p1, spoiler(p1.ctx));
            
            String colTxt = null;
            if (tag.equals("font") && c.hasAttr("color")) colTxt = c.attr("color");
            else if (c.hasAttr("data-mx-color")) colTxt = c.attr("data-mx-color");
            if (colTxt!=null) {
              Integer col = ColorUtils.parsePrefixed(colTxt);
              if (col!=null) p1 = wrap(p1, new TextNode(p1.ctx, Props.of("color", new ColProp(col))));
            }
            
            if (c.hasAttr("data-mx-bg-color")) {
              String bgTxt = c.attr("data-mx-bg-color");
              Integer col = ColorUtils.parsePrefixed(bgTxt);
              if (col!=null) p1 = wrap(p1, new TextNode(p1.ctx, Props.of("bg", new ColProp(col))));
            }
            
            rec(c, p1, mono, link, r);
            break;
          }
          case "ins":
            wrap(p, c, mono, link, r, Props.none());
            break;
          case "div": case "p": {
            TextNode n = new TextNode(p.ctx, Props.none());
            rec(c, n, mono, link, r);
            p.add(new InlineNode.FullBlock(n));
            break;
          }
          case "pill":
            if (c.hasAttr("mine") && c.hasAttr("id")) pill(r, p, c.text(), c.attr("id"), c.attr("mine").equals("true"));
            else wrap(p, c, mono, link, r, Props.none());
            break;
          case "ol":
            int num = c.hasAttr("start")? defInt(c.attr("start"), 1) : 1;
            for (Element c2 : c.children()) {
              p.add(new StringNode(p.ctx, num+". "));
              rec(c2, p, mono, link, r);
              p.add(new InlineNode.LineEnd(p.ctx, true));
              num++;
            }
            break;
          case "ul":
            for (Element c2 : c.children()) {
              p.add(new StringNode(p.ctx, "∘ "));
              rec(c2, p, mono, link, r);
              p.add(new InlineNode.LineEnd(p.ctx, true));
            }
            break;
          case "blockquote": {
            TextNode n = new TextNode(p.ctx, Props.of("color", p.gc.getCfgProp("chat.quote.color")));
            rec(c, n, mono, link, r);
            p.add(new InlineNode.FullBlock(new QuoteNode(n)));
            break;
          }
          default:
            ChatMain.warn("Unknown tag: "+c.tag());
            wrap(p, c, mono, link, r, Props.none());
        }
      }
    }
  }
  
  private static int defInt(String s, int def) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException f) {
      return def;
    }
  }
  
  public static Node pillLink(Chatroom r, Node ch, String uid) {
    TextNode n = new TextNode(ch.ctx, Props.none()) {
      public void mouseStart(int x, int y, Click c) { if (c.bR()) c.register(this, x, y); }
      public void mouseDown(int x, int y, Click c) { r.userMenu(c, x, y, uid); }
    };
    n.add(ch);
    return n;
  }
  private static void pill(Chatroom r, Node p, String text, String id, boolean mine) {
    p.add(pillLink(r, new Pill(p.ctx, r.user(), mine, id, text.startsWith("@")? text : "@"+text), id));
  }
  public static class Pill extends Node implements StringifiableNode {
    public final boolean mine;
    public final ChatUser u;
    public final String id, text;
    public Pill(Ctx ctx, ChatUser u, boolean mine, String id, String text) {
      super(ctx, Props.none());
      this.mine = mine;
      this.id = id;
      this.u = u;
      this.text = text;
    }
    int l, r, rr, bg, tw, col;
    public void propsUpd() { super.propsUpd();
      l = gc.getProp(mine? "chat.pill.padLMine" : "chat.pill.padL").len();
      r = gc.getProp(mine? "chat.pill.padRMine" : "chat.pill.padR").len();
      bg= gc.getProp(mine? "chat.pill.bgMine"   : "chat.pill.bg"  ).col();
      rr = gc.getProp("chat.pill.round").len();
      tw = gc.defFont.width(text);
      col = u.userCol(id, mine, true);
    }
    
    public void bg(Graphics g, boolean full) {
      pbg(g, full);
      g.rrect(0, 0, w, h, rr, bg);
    }
    
    public void drawC(Graphics g) {
      Font f = gc.defFont;
      StringNode.text(g, text, f, col, l, f.asc);
    }
    
    public int minW() { return tw+l+r; }
    public int maxW() { return tw+l+r; }
    public int minH(int w) { return gc.defFont.hi; }
    
    public String asString() { return id; }
  }
  
  public static String fixURL(String url) {
    String s = url.replace("[", "%5B").replace("]", "%5D").replace("(", "%28").replace("]", "%29");
    int i = s.indexOf('#')+1;
    if (i!=0) s = s.substring(0,i) + s.substring(i).replace("#", "%23");
    return s;
  }
  public static Pair<String, String> urlFrag(String url) {
    try {
      int i = url.indexOf('#');
      if (i==-1) return new Pair<>(url, null);
      String base = url.substring(0, i);
      if (base.endsWith("/")) base = base.substring(0, base.length()-1);
      return new Pair<>(base, new URI(url).getFragment());
    } catch (URISyntaxException e) {
      return new Pair<>(url, null);
    }
  }
  
  private static void wrap(TextNode n, Element c, boolean mono, String link, Chatroom r, String key) {
    PNodeGroup g = n.gc.getProp(key).gr();
    assert "text".equals(g.name);
    wrap(n, c, mono, link, r, Props.ofKV(g.ks, n.ctx.finishPropList(g, Ctx.NO_VARS)));
  }
  private static void wrap(TextNode p, Element c, boolean mono, String link, Chatroom r, Props props) {
    TextNode n = new TextNode(p.ctx, props);
    rec(c, n, mono, link, r);
    p.add(n);
  }
  private static TextNode wrap(TextNode p, TextNode n) {
    p.add(n);
    return n;
  }
  
  
  
  
  public static TextNode link(Chatroom r, String url, LinkType img) {
    return Extras.textLink(r.user(), url, img);
  }
  
  private static TextNode spoiler(Ctx ctx) {
    return new SpoilerNode(ctx);
  }
  private static class SpoilerNode extends TextNode {
    boolean open;
    private static final Props.Gen KEYS = Props.keys("hover", "xpad");
    public SpoilerNode(Ctx ctx) { super(ctx, KEYS.values(EnumProp.TRUE, ctx.gc.getProp("chat.spoiler.xpad"))); }
    
    public void drawCh(Graphics g, boolean full) {
      if (open) super.drawCh(g, full);
    }
    public void bg(Graphics g, boolean full) {
      int bg = gc.getProp("chat.spoiler.bg").col();
      if (isSingleLine()) {
        g.rect(sX, sY1, eX, eY2, bg);
      } else {
        g.rect(sX, sY1, w, sY2, bg);
        if (sY2<eY1) g.rect(0, sY2, w, eY1, bg);
        g.rect(0, eY1, eX, eY2, bg);
      }
    }
    public void mouseStart(int x, int y, Click c) {
      if (open) super.mouseStart(x, y, c);
      if (c.bL()) c.register(this, x, y);
    }
    public void mouseTick(int x, int y, Click c) { c.onClickEnd(); }
    public void mouseUp(int x, int y, Click c) {
      if (!visible) return;
      open^= true;
      mRedraw();
    }
  }
  
  
  
  private static final Props I_PROP = Props.of("italics", EnumProp.TRUE);
  private static final Props B_PROP = Props.of("bold", EnumProp.TRUE);
  private static final Props S_PROP = Props.of("strike", EnumProp.TRUE);
  private static final Props U_PROP = Props.of("underline", EnumProp.TRUE);
}
