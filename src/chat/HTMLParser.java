package chat;

import chat.ui.*;
import chat.ui.Extras.LinkType;
import dzaima.ui.eval.*;
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
    if (ss>=se) return new TextNode(r.m.base.ctx, Node.KS_NONE, Node.VS_NONE);
    s = s.substring(ss, se);
    Element b = Jsoup.parse(s).body();
    TextNode base = new TextNode(r.m.base.ctx, Node.KS_NONE, Node.VS_NONE);
    rec(b, base, false, null, r);
    return base;
  }
  
  public static Node inlineImage(ChatUser u, String link, boolean dataIsFull, byte[] data, BiFunction<Ctx, byte[], ImageNode> ctor) {
    Ctx ctx = u.m.base.ctx;
    ImageNode img = ctor.apply(ctx, data);
    if (!img.loadableImage()) return null;
    TextNode l = Extras.textLink(u, link, LinkType.IMG, dataIsFull? data : null);
    InlineNode.TANode v = new InlineNode.TANode(ctx, new String[]{"width"}, new Prop[]{new EnumProp("max")});
    v.add(img);
    l.add(v);
    return l;
  }
  
  static class QuoteNode extends PadCNode {
    public QuoteNode(Node ch) {
      super(ch.ctx, ch, 1, 0, 0.2f, 0.2f);
    }
    
    public void bg(Graphics g, boolean full) {
      pbg(g, full);
      g.rect(0, 0, gc.em/4f, h, gc.getProp("chat.quote.line").col());
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
              String url = fixURL(s.substring(start, end));
              if (url.equals(link)) continue;
              try {
                URI uri = new URI(url);
                if (pi != start) p.add(new StringNode(p.ctx, s.substring(pi, start)));
                Node inner = p;
                if (link==null) p.add(inner = link(r, url, LinkType.UNK));
                String txt = uri.getHost()+"/";
                trunc: { int L = 100;
                  if (!uri.getScheme().equals("https")) txt = uri.getScheme()+"://"+txt;
                  String[] path = uri.getPath().split("/");
                  int j = 0;
                  while (j<path.length) {
                    if (path[j].length()==0) { j++; continue; }
                    String n = txt+path[j]+"/";
                    if (n.length()>L) { txt+="…"; break trunc; }
                    txt = n; j++;
                  }
                  String q = uri.getQuery();
                  if (q!=null) {
                    String n = txt+"?"+q;
                    if (n.length()>L) { txt+="?…"; break trunc; }
                    txt = n;
                  }
                  String h = uri.getFragment();
                  if (h!=null) {
                    String n = txt+"#"+h;
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
            p.add(new StringNode(p.ctx, "\n"));
            break;
          case "a":
            String url = fixURL(c.attr("href"));
            if (url.startsWith("https://matrix.to/#/@")) {
              String id = url.substring(20);
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
            wrap(p, c, mono, link, r, iKey, trueValue);
            break;
          case "strike": case "del":
            wrap(p, c, mono, link, r, sKey, trueValue);
            break;
          case "b": case "strong":
            wrap(p, c, mono, link, r, bKey, trueValue);
            break;
          case "u":
            wrap(p, c, mono, link, r, uKey, trueValue);
            break;
          case "pre":
            p.add(InlineNode.FullBlock.wrap(pre(p.ctx, c.wholeText())));
            break;
          case "span": case "font": {
            TextNode p1 = p;
            if (c.hasAttr("data-mx-spoiler")) p1 = wrap(p1, spoiler(p1.ctx));
            
            String colTxt = null;
            if (tag.equals("font") && c.hasAttr("color")) colTxt = c.attr("color");
            else if (c.hasAttr("data-mx-color")) colTxt = c.attr("data-mx-color");
            if (colTxt!=null) {
              Integer col = ColorUtils.parsePrefixed(colTxt);
              if (col!=null) p1 = wrap(p1, new TextNode(p1.ctx, new String[]{"color"}, new Prop[]{new ColProp(col)}));
            }
            
            if (c.hasAttr("data-mx-bg-color")) {
              String bgTxt = c.attr("data-mx-bg-color");
              Integer col = ColorUtils.parsePrefixed(bgTxt);
              if (col!=null) p1 = wrap(p1, new TextNode(p1.ctx, new String[]{"bg"}, new Prop[]{new ColProp(col)}));
            }
            
            rec(c, p1, mono, link, r);
            break;
          }
          case "ins":
            wrap(p, c, mono, link, r, Node.KS_NONE, Node.VS_NONE);
            break;
          case "div": case "p":
            wrap(p, c, mono, link, r, Node.KS_NONE, Node.VS_NONE);
            p.add(new InlineNode.LineEnd(p.ctx, true));
            break;
          case "pill":
            if (c.hasAttr("mine") && c.hasAttr("id")) pill(r, p, c.text(), c.attr("id"), c.attr("mine").equals("true"));
            else wrap(p, c, mono, link, r, Node.KS_NONE, Node.VS_NONE);
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
          case "blockquote":
            STextNode n = new STextNode(p.ctx, new String[]{"color"}, new Prop[]{p.gc.getCfgProp("chat.quote.color")});
            wrap(n, c, mono, link, r, Node.KS_NONE, Node.VS_NONE);
            p.add(InlineNode.FullBlock.wrap(new QuoteNode(n)));
            break;
          default:
            ChatMain.warn("Unknown tag: "+c.tag());
            wrap(p, c, mono, link, r, Node.KS_NONE, Node.VS_NONE);
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
    TextNode n = new TextNode(ch.ctx, Node.KS_NONE, Node.VS_NONE) {
      public void mouseStart(int x, int y, Click c) { if (c.bR()) c.register(this, x, y); }
      public void mouseDown(int x, int y, Click c) { r.userMenu(c, x, y, uid); }
    };
    n.add(ch);
    return n;
  }
  private static void pill(Chatroom r, Node p, String text, String id, boolean mine) {
    p.add(pillLink(r, new Pill(p.ctx, r.user(), mine, id, "@"+text), id));
  }
  public static class Pill extends Node implements StringifiableNode {
    public final boolean mine;
    public final ChatUser u;
    public final String id, text;
    public Pill(Ctx ctx, ChatUser u, boolean mine, String id, String text) {
      super(ctx, KS_NONE, VS_NONE);
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
    url = url.replace("[", "%5B").replace("]", "%5D").replace("(", "%28").replace("]", "%29");
    int h = url.indexOf("#");
    if (h!=-1) url = url.substring(0, h+1) + url.substring(h+1).replace("#", "%23");
    return url;
  }
  
  private static void wrap(TextNode n, Element c, boolean mono, String link, Chatroom r, String key) {
    PNodeGroup g = n.gc.getProp(key).gr();
    assert "text".equals(g.name);
    wrap(n, c, mono, link, r, g.ks, n.ctx.finishProps(g, Ctx.NO_VARS));
  }
  private static void wrap(TextNode p, Element c, boolean mono, String link, Chatroom r, String[] k, Prop[] v) {
    TextNode n = new TextNode(p.ctx, k, v);
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
    public SpoilerNode(Ctx ctx) { super(ctx, new String[]{"hover", "xpad"}, new Prop[]{EnumProp.TRUE, ctx.gc.getProp("chat.spoiler.xpad")}); }
    
    public void drawCh(Graphics g, boolean full) {
      if (open) super.drawCh(g, full);
    }
    public void bg(Graphics g, boolean full) {
      int bg = gc.getProp("chat.spoiler.bg").col();
      if (sY1==eY1) {
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
  
  
  
  private static final String[] iKey = {"italics"};
  private static final String[] bKey = {"bold"};
  private static final String[] sKey = {"strike"};
  private static final String[] uKey = {"underline"};
  private static final Prop[] trueValue = {EnumProp.TRUE};
}
