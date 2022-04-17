package chat.ui;

import chat.ChatMain;
import io.github.humbleui.skija.*;

public class Animation {
  public boolean valid;
  
  public Codec c;
  int[] starts; // start of each frame
  public final int duration; // in milliseconds
  public final int w, h;
  
  public Animation(byte[] data) {
    int time = 0;
    Codec c0;
    int w0 = -1;
    int h0 = -1;
    try {
      c0 = Codec.makeFromData(Data.makeFromBytes(data));
      AnimationFrameInfo[] fi = c0.getFramesInfo();
      starts = new int[fi.length];
      for (int i = 0; i < fi.length; i++) {
        starts[i] = time;
        time+= fi[i].getDuration();
      }
      w0 = c0.getWidth();
      h0 = c0.getHeight();
      valid = true;
    } catch (Exception ex) {
      ChatMain.warn("Failed to load animation:");
      ex.printStackTrace();
      c0 = null;
    }
    if (starts==null || starts.length==0) starts = new int[]{0};
    w = w0;
    h = h0;
    c = c0;
    duration = time;
  }
  
  public int frameCount() {
    return starts.length;
  }
  
  public int nextFrameTime(int ms) {
    int f = findFrame(ms)+1;
    if (f==frameCount()) f = 0;
    return starts[f];
  }
  public int prevFrameTime(int ms) {
    int f = findFrame(ms);
    f = f==0? frameCount()-1 : f-1;
    return starts[f];
  }
  
  public int frameTime(int n) {
    return starts[n];
  }
  
  public int findFrame(long ms) { // find frame at time ms
    int s = 0;
    int e = starts.length;
    while (s+1 < e) {
      int m = (s+e) / 2;
      if (ms>=starts[m]) s = m;
      else               e = m;
    }
    return s;
  }
  
  int pFrame = -1;
  Image pImage;
  Bitmap b;
  public Image frame(int cFrame) {
    if (!valid) return null;
    if (b == null) {
      b = new Bitmap();
      b.allocPixels(c.getImageInfo());
      pFrame = -1;
    }
    
    if (cFrame!=pFrame || pImage==null) {
      try {
        closeImage();
        if (pFrame!=-1 && pFrame+1 == cFrame) c.readPixels(b, cFrame, pFrame);
        else c.readPixels(b, cFrame);
        pFrame = cFrame;
        pImage = Image.makeFromBitmap(b);
      } catch (Exception ex) { ChatMain.warn("Failed to animate:"); ex.printStackTrace(); valid = false; }
    }
    return pImage;
  }
  
  private void closeImage () { if (pImage!=null) { pImage.close(); pImage = null; } }
  private void closeCodec () { if (c     !=null) {      c.close(); c = null; } }
  private void closeBitmap() { if (b     !=null) {      b.close(); b = null; } }
  
  public void hidden() { // don't expect frames to be drawn soon
    closeImage();
    closeBitmap();
  }
  
  public void close() { // release all resources
    hidden();
    closeCodec();
  }
}
