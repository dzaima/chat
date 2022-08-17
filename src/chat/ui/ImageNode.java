package chat.ui;

import dzaima.ui.gui.Graphics;
import dzaima.ui.gui.config.GConfig;
import dzaima.ui.node.Node;
import dzaima.ui.node.ctx.Ctx;
import dzaima.utils.Tools;
import io.github.humbleui.skija.Image;

public abstract class ImageNode extends Node {
  byte[] data;
  Image f0;
  
  public ImageNode(Ctx ctx, byte[] data) {
    super(ctx, KS_NONE, VS_NONE);
    this.data = data;
    try {
      if (data!=null) f0 = Image.makeFromEncoded(data);
    } catch (Throwable e) {
      f0 = null;
    }
  }
  
  public static float scaleMax(GConfig gc, int iw, int ih, int maxW, int maxH) { return Math.min(Math.min(maxW*1f/iw, maxH*1f/ih), gc.imgScale); }
  public static float scaleMin(GConfig gc, int iw, int ih, int minW, int minH) { return Math.max(Math.max(minW*1f/iw, minH*1f/ih), gc.imgScale); }
  
  // maximum image size; either being hit will limit the size
  public abstract int maxTotW();
  public abstract int maxTotH();
  // minimum bounding box of the image; if smaller than that on one axis (or both in the case of a 0×0 image), will be padded to be at least that
  // if greater than corresponding maxTot*, assumed to be that instead
  public abstract int minTotW();
  public abstract int minTotH();
  
  float scale, aspect;
  int iw, ih, wMin, hMin, wMax, hMax;
  public void propsUpd() {
    if (f0!=null) {
      iw = f0.getWidth();
      ih = f0.getHeight();
    } else iw=ih=0;
    
    wMax = maxTotW();
    hMax = maxTotH();
    wMin = Math.min(minTotW(), wMax);
    hMin = Math.min(minTotH(), hMax);
    scale = gc.imgScale;
    if (iw>0 && ih>0) {
      aspect = iw*1f/ih;
      wMax = Tools.constrain((int) Math.min(hMax*aspect, iw*scale), wMin, wMax);
    } else {
      aspect = 1f;
      wMax = wMin;
      hMax = hMin;
    }
  }
  
  public int minW() { return wMin; }
  public int maxW() { return wMax; }
  
  public int minH(int w) {
    w = Math.min(w, wMax);
    return Tools.constrain((int) (w/aspect), hMin, hMax);
  }
  public int maxH(int w) {
    return minH(w);
  }
  
  boolean animInitiated;
  Animation anim;
  public void drawC(Graphics g) {
    if (hovered) {
      if (data!=null && !animInitiated) {
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
    float sc = Math.min(w*1f/iw,
                        h*1f/ih);
    if (i!=null) g.image(i, 0, 0, (int) (iw*sc), (int) (ih*sc), Graphics.Sampling.LINEAR_MIPMAP);
  }
  
  boolean hovered = false;
  long animStart;
  public void hoverS() { mRedraw(); hovered = true; animStart = System.currentTimeMillis(); }
  public void hoverE() { mRedraw(); hovered = false; if (anim!=null) anim.hidden(); }
  
  
  public static class EmojiImageNode extends ImageNode {
    public EmojiImageNode(Ctx ctx, byte[] data) { super(ctx, data); }
    public int minTotW() { return gc.em; }
    public int minTotH() { return gc.em; }
    public int maxTotW() { return gc.em*4; }
    public int maxTotH() { return gc.em; }
  }
  public static class InlineImageNode extends ImageNode {
    public InlineImageNode(Ctx ctx, byte[] data) { super(ctx, data); }
    public int minTotW() { return gc.len(this, "minW", "chat.image.minW"); }
    public int minTotH() { return gc.len(this, "minH", "chat.image.minH"); }
    public int maxTotW() { return gc.len(this, "maxW", "chat.image.maxW"); }
    public int maxTotH() { return gc.len(this, "maxH", "chat.image.maxH"); }
  }
  public static class ProfilePictureNode extends ImageNode {
    public ProfilePictureNode(Ctx ctx, byte[] data) { super(ctx, data); }
    public int minTotW() { return gc.len(this, "minW", "chat.profile.minW"); }
    public int minTotH() { return gc.len(this, "minH", "chat.profile.minH"); }
    public int maxTotW() { return gc.len(this, "maxW", "chat.profile.maxW"); }
    public int maxTotH() { return gc.len(this, "maxH", "chat.profile.maxH"); }
  }
  public static class UserListAvatarNode extends ImageNode {
    public UserListAvatarNode(Ctx ctx, byte[] data) { super(ctx, data); }
    public int minTotW() { return gc.len(this, "minW", "chat.userList.avatarSz"); }
    public int minTotH() { return gc.len(this, "minH", "chat.userList.avatarSz"); }
    public int maxTotW() { return gc.len(this, "maxW", "chat.userList.avatarSz"); }
    public int maxTotH() { return gc.len(this, "maxH", "chat.userList.avatarSz"); }
  }
}
