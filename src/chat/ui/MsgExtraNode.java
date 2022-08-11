package chat.ui;

import chat.*;
import dzaima.ui.eval.PNodeGroup;
import dzaima.ui.gui.*;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.types.*;
import dzaima.utils.*;
import io.github.humbleui.skija.paragraph.*;

import java.util.*;
import java.util.function.Consumer;

public class MsgExtraNode extends InlineNode {
  private final Chatroom r;
  private final HashSet<String> receipts;
  private final ArrayList<Map.Entry<String, Integer>> reactions;
  
  public MsgExtraNode(Ctx ctx, Chatroom r, HashMap<String, Integer> reactions, HashSet<String> receipts) {
    super(ctx, KS_NONE, VS_NONE);
    this.r = r;
    if (reactions==null) {
      this.reactions = null;
    } else {
      this.reactions = new ArrayList<>(reactions.entrySet());
      this.reactions.sort(Comparator.comparingInt((Map.Entry<String, Integer> a) -> a.getValue()).thenComparing(Map.Entry::getKey));
    }
    this.receipts = receipts;
  }
  
  private int th,wt,wr;
  private Paragraph pr, pv;
  public static int maxReactions = 3;
  public void propsUpd() { super.propsUpd();
    int h = 0;
  
    if (reactions!=null) {
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
      
      pr = Graphics.paragraph(Graphics.textStyle(gc.getProp("chat.reaction.family"), gc.getProp("chat.reaction.col").col(), gc.getProp("chat.reaction.size").lenF()), b.toString());
      wr = Tools.ceil(pr.getMaxIntrinsicWidth());
      h = Math.max(Tools.ceil(pr.getHeight()), h);
    } else {
      pr = null;
      wr = 0;
    }
  
    int wv;
    if (receipts!=null) {
      pv = Graphics.paragraph(Graphics.textStyle(gc.getProp("chat.receipt.family"), gc.getProp("chat.receipt.col").col(), gc.getProp("chat.receipt.size").lenF()), " ⦿ "+receipts.size());
      wv = Tools.ceil(pv.getMaxIntrinsicWidth());
      h = Math.max(Tools.ceil(pv.getHeight()), h);
    } else {
      pv = null;
      wv = 0;
    }
    
    wt = wv + wr + gc.em/5;
    th = h;
  }
  
  protected void baseline(int asc, int dsc, int h) { }
  protected void addInline(InlineSolver sv) {
    if (sv.w-sv.x < wt) {
      sv.nl();
      sX = (short) sv.x;
      sY1 = (short) sv.y;
    }
    sv.h = Math.max(sv.h, th);
    sv.x = sv.w;
  }
  
  public void drawC(Graphics g) {
    if (pr!=null) pr.paint(g.canvas, w-wt   , eY1);
    if (pv!=null) pv.paint(g.canvas, w-wt+wr, eY1);
  }
  
  public void hoverS() { r.m.msgExtra = this; }
  public void hoverE() { r.m.msgExtra = null; }
  
  boolean rHovered(XY me) {
    NodeVW vw = ctx.vw();
    int mx = vw.mx-me.x;
    int my = vw.my-me.y;
    return mx >= w-wt+wr && mx<w && my>=eY1 && my<eY2;
  }
  
  public void tickExtra() {
    XY me = relPos(null);
    if (receipts!=null && rHovered(me) && r.m.hoverPopup==null) {
      PNodeGroup g = gc.getProp("chat.receipt.list").gr().copy();
      
      NodeVW[] vwh = new NodeVW[1];
      HoverPopup popup = new HoverPopup(ctx, s -> {
        if (s.equals("(closed)")) r.m.hoverPopup = null;
      }) {
        public boolean shouldClose() { return !rHovered(MsgExtraNode.this.relPos(null)) && !vwh[0].mIn; }
      };
      
      r.m.hoverPopup = popup;
      NodeVW vw = popup.openVW(gc, ctx, g, true);
      vwh[0] = vw;
      
      ArrayList<String> rs = new ArrayList<>();
      for (String c : receipts) {
        rs.add(r.getUsername(c));
      }
      Collections.sort(rs);
      for (String r : rs) popup.node.add(new MenuNode.MINode(vw.base.ctx, r, ""));
      vw.newRect();
    }
  }
  
  public static abstract class HoverPopup extends Popup.RightClickMenu {
    public HoverPopup(Ctx ctx, Consumer<String> action) {
      super(ctx, action);
    }
    public abstract boolean shouldClose();
  }
}