package chat.ui;

import dzaima.ui.gui.Graphics;
import dzaima.ui.gui.config.GConfig;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import io.github.humbleui.skija.Image;

public class ImageNode extends Node {
  byte[] data;
  Image f0;
  
  public ImageNode(Ctx ctx, byte[] data) {
    super(ctx, KS_NONE, VS_NONE);
    this.data = data;
    try {
      f0 = Image.makeFromEncoded(data);
    } catch (Exception e) {
      f0 = null;
    }
  }
  
  public static float fitScale(GConfig gc, int iw, int ih, int mw, int mh) {
    return Math.min(Math.min(mw*1f/iw, mh*1f/ih), gc.imgScale);
  }
  
  int aw, ah;
  public void propsUpd() {
    if (f0!=null) {
      int iw = f0.getWidth();
      int ih = f0.getHeight();
      
      int mw = gc.getProp("chat.image.maxW").len();
      int mh = gc.getProp("chat.image.maxH").len();
  
      float sc = fitScale(gc, iw, ih, mw, mh);
    
      aw = (int) (iw*sc);
      ah = (int) (ih*sc);
    }
  }
  
  boolean animInitiated;
  Animation anim;
  public void drawC(Graphics g) {
    if (hovered) {
      if (!animInitiated) {
        animInitiated = true;
        anim = new Animation(data);
        if (anim.frameCount()<=1) {
          anim.close();
          anim = null;
        }
        data = null;
      }
      if (anim!=null) {
        drawImg(g, anim.frame(anim.findFrame((System.currentTimeMillis()-animStart) % anim.duration)));
        mRedraw();
        return;
      }
    }
    
    drawImg(g, f0);
  }
  
  private void drawImg(Graphics g, Image i) {
    if (i!=null) g.image(i, 0, 0, aw, ah, Graphics.Sampling.LINEAR_MIPMAP);
  }
  
  public int minW() { return aw; }
  public int minH(int w) { return ah; }
  
  boolean hovered = false;
  long animStart;
  public void hoverS() { mRedraw(); hovered = true; animStart = System.currentTimeMillis(); }
  public void hoverE() { mRedraw(); hovered = false; if (anim!=null) anim.hidden(); }
}
