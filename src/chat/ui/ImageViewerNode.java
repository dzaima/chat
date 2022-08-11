package chat.ui;

import dzaima.ui.gui.Graphics;
import dzaima.ui.gui.io.Click;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.ui.node.prop.Prop;

public class ImageViewerNode extends Node {
  public ImageViewerNode(Ctx ctx, String[] ks, Prop[] vs) {
    super(ctx, ks, vs);
    lastMs = System.currentTimeMillis();
    aTick();
  }
  
  public Animation anim;
  int tlH;
  public void setAnim(Animation a) {
    anim = a;
    tlH = anim.frameCount()>1? gc.em : 0;
  }
  
  public int gh() { return h - tlH; }
  
  public void zoomToFit() {
    int h = gh();
    zoom(w/2, h/2, ImageNode.scaleMax(gc, anim.w, anim.h, w, h));
    move(w/2, h/2);
  }
  
  boolean playing = true;
  public void playPause() { playing^= true; mRedraw(); }
  public void nextFrame() { animTime = anim.nextFrameTime(animTime); mRedraw(); }
  public void prevFrame() { animTime = anim.prevFrameTime(animTime); mRedraw(); }
  public void toFrame(int n) { animTime = anim.frameTime(n); mRedraw(); }
  
  int animTime;
  long lastMs;
  int pFrame = -1;
  public void tickC() {
    if (anim.frameCount()>1) {
      long currMs = System.currentTimeMillis();
      if (playing && !timelineDrag) {
        animTime+= currMs-lastMs;
      }
      animTime%= anim.duration;
      lastMs = currMs;
      
      int cFrame = anim.findFrame(animTime);
      if (pFrame!=cFrame) {
        mRedraw();
        pFrame = cFrame;
      }
    } else {
      pFrame = 0;
    }
  }
  
  public void drawC(Graphics g) {
    int h = gh();
    
    g.push();
    g.clip(0, 0, w, h);
    g.translateLocal((int)(drx*sc), (int)(dry*sc));
    if (sc!=1) g.scaleLocal(sc, sc);
    
    g.image(anim.frame(pFrame), 0, 0, anim.w, anim.h, sc<2? Graphics.Sampling.LINEAR_MIPMAP : Graphics.Sampling.NEAREST);
    g.pop();
    
    g.push();
    g.translateLocal(0, h);
    g.clip(0, 0, w, tlH);
    if (tlH>0) {
      int frac = animTime*w/anim.duration;
      g.rect(0, 0, frac, h, gc.getProp("imageViewer.timelineDone").col());
      g.rect(frac, 0, w, h, gc.getProp("imageViewer.timelineLeft").col());
    }
    g.pop();
  }
  
  float drx, dry, sc=1;
  public boolean scroll(int x, int y, float dx, float dy) {
    float zoomStrength = gc.getProp("imageViewer.zoomStrength").f();
    float nsc = (float) (sc*Math.pow(zoomStrength, dy+dx));
    if      (sc>1 && nsc<1) nsc = 1;
    else if (sc<1 && nsc>1) nsc = 1;
    if (nsc> 1e3) nsc = 1e3f;
    if (nsc<1e-2) nsc = 1e-2f;
    if (nsc>0.999 && nsc<1.001) nsc = 1;
    
    zoom(x, y, nsc);
    
    mRedraw();
    return true;
  }
  
  public void zoom(int x, int y, float zoom) {
    drx-= x/sc; dry-= y/sc;
    sc = zoom;
    drx+= x/sc; dry+= y/sc;
    mRedraw();
  }
  
  boolean timelineDrag;
  public void mouseStart(int x, int y, Click c) {
    if (c.bL() || c.bC()) {
      c.register(this, x, y);
      timelineDrag = y > h-tlH;
    }
  }
  public void mouseUp(int x, int y, Click c) { timelineDrag = false; }
  
  public void mouseTick(int x, int y, Click c) {
    if (c.bL() || c.bC()) {
      if (timelineDrag) {
        animTime = Math.min(Math.max(0, x), w-1)*anim.duration / w;
      } else {
        drx+= c.dx/sc;
        dry+= c.dy/sc;
      }
      mRedraw();
    }
  }
  
  public void move(int x, int y) {
    drx = x/sc - anim.w/2f;
    dry = y/sc - anim.h/2f;
    mRedraw();
  }
  public void center() {
    move(w/2, gh()/2);
  }
}
