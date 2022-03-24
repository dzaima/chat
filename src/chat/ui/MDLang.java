package chat.ui;

import chat.*;
import dzaima.ui.gui.Font;
import dzaima.ui.node.types.editable.EditNode;
import dzaima.ui.node.types.editable.code.*;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.paragraph.*;

public class MDLang extends Lang {
  public TextStyle[] styles;
  public TextStyle style(byte v) {
    return styles[v];
  }
  
  public MDLang(Font f, ChatMain m, ChatTextArea ta) {
    super(new MDState(m, ta));
    styles = new TextStyle[36];
    int defTextCol = m.gc.getProp("str.color").col();
    int spoilerCol = m.gc.getProp("chat.preview.spoilerBg").col();
    int codeCol = m.gc.getProp("chat.preview.codeBg").col(); // TODO separate bgInline and bgBlock
    int escCol = m.gc.getProp("chat.preview.escape").col();
    String codeFamily = m.gc.getProp("chat.preview.codeFamily").str();
    for (int i = 0; i < styles.length; i++) {
      TextStyle s = new TextStyle().setFontSize(f.sz);
      int col = defTextCol;
      int bgCol = 0;
      String family = f.tf.name;
      if ((i&MDParser.S_MASK) == MDParser.S_DEF) {
        boolean b = (i&MDParser.SD_B)!=0;
        s.setFontStyle((i&MDParser.SD_I)!=0? (b? FontStyle.BOLD_ITALIC : FontStyle.ITALIC) : (b? FontStyle.BOLD : FontStyle.NORMAL));
        s.setDecorationStyle(DecorationStyle.NONE.withLineThrough((i&MDParser.SD_ST)!=0).withColor(defTextCol));
        if ((i&MDParser.SD_SP)!=0) bgCol = spoilerCol;
      } else {
        if (i==MDParser.S_CODE_ESC || i==MDParser.S_CODE) {
          bgCol = codeCol;
          family = codeFamily;
        }
        if (i==MDParser.S_CODE_ESC || i==MDParser.S_DEF_ESC) col = escCol;
        if (i==MDParser.S_LINK) col = m.gc.getProp("chat.link.col").col();
      }
      if (bgCol!=0) s.setBackground(new Paint().setColor(bgCol));
      s.setFontFamily(family);
      s.setColor(col);
      styles[i] = s;
    }
  }
  public Lang font(Font f) { return new TextLang(f); }
  
  static class MDState extends LangState<MDState> {
    private final ChatMain m;
    private final ChatTextArea ta;
    MDState(ChatMain m, ChatTextArea ta) {
      this.ta = ta;
      this.m = m;
    }
    public MDState after(int sz, char[] p, byte[] b) {
      String s = ta.getAll();
      Chatroom r = m.room;
      int[] styles = r==null || r.highlight(s)? MDParser.eval(s, c->"").styles : new int[s.length()];
      int cx = 0;
      for (EditNode.Line l : ta.lns) {
        int lsz = l.sz();
        byte[] bs = l.st.arr;
        for (int i = 0; i < lsz; i++) {
          bs[i] = (byte)styles[cx+i];
        }
        cx+= lsz+1;
      }
      return this;
    }
    public boolean equals(Object obj) { return obj instanceof MDState; }
    public int hashCode() { return 0; }
  }
  
  public static Language makeLanguage(ChatMain m, ChatTextArea ta) {
    return new Language("Markdown", new String[0], (font) -> new MDLang(font, m, ta));
  }
}
