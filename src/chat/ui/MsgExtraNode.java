package chat.ui;

import chat.*;
import chat.mx.ViewProfile;
import dzaima.ui.gui.*;
import dzaima.ui.gui.config.GConfig;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Props;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import io.github.humbleui.skija.paragraph.Paragraph;

import java.util.*;
import java.util.function.*;

public class MsgExtraNode extends InlineNode {
  public static int maxReactions = 3;
  
  private final Chatroom r;
  private final HashSet<String> receipts;
  private final ParaNode receiptPara;
  
  public MsgExtraNode(Ctx ctx, ChatEvent e, HashMap<String, Integer> reactions, HashSet<String> receipts, boolean hasThread) {
    super(ctx, Props.none());
    this.r = e.room();
    this.receipts = receipts;
    
    HlNode l = new HlNode(ctx, ctx.finishProps(ctx.gc.getProp("chat.msg.extra.hlProps").gr(), null));
    add(l);
    
    if (reactions!=null) {
      ArrayList<Map.Entry<String, Integer>> reactionCounts = new ArrayList<>(reactions.entrySet());
      reactionCounts.sort(Comparator.comparingInt((Map.Entry<String, Integer> a) -> a.getValue()).thenComparing(Map.Entry::getKey));
      l.add(new ParaNode(ctx, () -> reactionPara(ctx.gc, reactionCounts)));
    }
    
    if (receipts!=null) {
      receiptPara = new ParaNode(ctx, () -> receiptPara(ctx.gc, receipts));
      receiptPara.add(ctx.makeHere(gc.getProp("chat.icon.read").gr()));
      l.add(receiptPara);
    } else {
      receiptPara = null;
    }
    
    if (hasThread) {
      l.add(new WrapNode(ctx, ctx.make(ctx.gc.getProp("chat.msg.openThread").gr())) {
        public int minW() { return maxW(); }
        
        public void mouseStart(int x, int y, Click c) {
          super.mouseStart(x, y, c);
          c.register(this, x, y);
        }
        
        public void mouseTick(int x, int y, Click c) {
          c.onClickEnd();
        }
        public void mouseUp(int x, int y, Click c) {
          if (visible && c.bL()) e.toThread();
        }
      });
    }
  }
  
  private static Paragraph receiptPara(GConfig gc, HashSet<String> receipts) {
    return Graphics.paragraph(Graphics.textStyle(gc.getProp("chat.receipt.family"), gc.getProp("chat.receipt.col").col(), gc.getProp("chat.receipt.size").lenF()), Integer.toString(receipts.size()));
  }
  
  private static Paragraph reactionPara(GConfig gc, ArrayList<Map.Entry<String, Integer>> reactions) {
    StringBuilder b = new StringBuilder();
    if (reactions.size()>maxReactions) b.append(reactions.size()-maxReactions).append(" + ");
    for (int i = reactions.size()-Math.min(reactions.size(), maxReactions); i < reactions.size(); i++) {
      Map.Entry<String, Integer> e = reactions.get(i);
      int v = e.getValue();
      String k = e.getKey();
      int cl = 0;
      int ci = 0;
      while (ci<k.length()) {
        cl++;
        ci+= Character.charCount(k.codePointAt(ci));
      }
      if (cl>4) k = "……";
      b.append(k).append(v==1? " " : "×"+v+" ");
    }
    if (b.length()>0 && b.charAt(b.length()-1)==' ') b.deleteCharAt(b.length()-1);
    
    return Graphics.paragraph(Graphics.textStyle(gc.getProp("chat.reaction.family"), gc.getProp("chat.reaction.col").col(), gc.getProp("chat.reaction.size").lenF()), b.toString());
  }
  
  public static Node createEnd(ChatEvent ce) {
    HashMap<String, Integer> rs = ce.getReactions();
    HashSet<String> vs = ce.getReceipts(ce.m().view);
    boolean hasThread = ce.startsThread(ce.m().view);
    return rs!=null || vs!=null || hasThread? new MsgExtraNode(ce.m().ctx, ce, rs, vs, hasThread) : new LineEnd(ce.m().ctx, false);
  }
  
  public void hoverS() { r.m.msgExtra = this; }
  public void hoverE() { r.m.msgExtra = null; }
  
  protected void baseline(int asc, int dsc, int h) { }
  protected void addInline(InlineSolver sv) {
    Node c = ch.get(0);
    int w = c.minW() + gc.em/5; // TODO theme?
    int h = c.minH(w);
    if (sv.w-sv.x < w) {
      sv.nl();
      sX = (short) sv.x;
      sY1 = (short) sv.y;
    }
    sv.h = Math.max(sv.h, h);
    sv.x = sv.w;
    if (sv.resize) c.resize(w, h, sv.w-w, sv.y);
  }
  
  public void tickExtra() {
    if (r.m.hoverPopup!=null) return;
    if (receiptPara!=null && receiptPara.hover) {
      hoverPopup(receiptPara, Vec.ofCollection(receipts).map(c -> new Pair<>(r.getUsername(c, true), () -> ViewProfile.viewProfile(c, r))));
    }
  }
  private void hoverPopup(ParaNode source, Vec<Pair<Chatroom.Username, Runnable>> lines) {
    Box<NodeVW> vw1 = new Box<>();
    PartialMenu pm = new PartialMenu(gc);
    lines.sort(Comparator.comparing(c -> c.a.best()));
    Vec<Pair<Chatroom.Username, String>> us = lines.map(l -> new Pair<>(l.a, pm.add(l.a.best(), l.b)));
    Popup.RightClickMenu m = r.m.hoverPopup = pm.openCustom(null, () -> r.m.hoverPopup = null, (gr, c) -> {
      HoverPopup p = new HoverPopup(ctx, c) {
        public boolean shouldClose() { return !source.hover && !vw1.get().mIn; }
      };
      vw1.set(p.openVW(gc, ctx, gr, true));
      return p;
    });
    
    for (Pair<Chatroom.Username, String> u : us) {
      if (!u.a.full.isResolved()) u.a.full.then(full -> PartialMenu.updateText(m, u.b, full));
    }
  }
  
  public static abstract class HoverPopup extends Popup.RightClickMenu {
    public HoverPopup(Ctx ctx, Consumer<String> action) {
      super(ctx, action);
    }
    public abstract boolean shouldClose();
  }
  
  private static class ParaNode extends Node {
    private final Supplier<Paragraph> gen;
    private Paragraph para;
    private int mw=-1, mh=-1;
    
    public ParaNode(Ctx ctx, Supplier<Paragraph> gen) {
      super(ctx, Props.none());
      this.gen = gen;
    }
    
    public void propsUpd() { super.propsUpd();
      para = gen.get();
      mw=mh=-1;
    }
    
    private void upd() {
      if (mw!=-1) return;
      mw = Tools.ceil(para.getMaxIntrinsicWidth());
      mh = Tools.ceil(para.getHeight());
      if (ch.sz>0) {
        Node c = ch.get(0);
        int cw = c.minW();
        mw+= cw;
        mh = Math.max(mh, c.minH(cw));
      }
    }
    
    protected void resized() {
      upd();
      if (ch.sz==0) return;
      Node c = ch.get(0);
      int cw = c.minW();
      int ch = c.minH(cw);
      c.resize(cw, ch, 0, (h-ch)/2);
    }
    
    private boolean hover;
    public void hoverS() { hover = true; }
    public void hoverE() { hover = false; }
    
    public void drawC(Graphics g) {
      para.paint(g.canvas, ch.sz==0? 0 : ch.get(0).w, (h-para.getHeight())/2);
    }
    
    public int minW() { upd(); return mw; }
    public int maxW() { upd(); return mw; }
    public int minH(int w) { upd(); return mh; }
    public int maxH(int w) { upd(); return mh; }
  }
}