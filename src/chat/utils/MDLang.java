package chat.utils;

import chat.*;
import chat.ui.ChatTextArea;
import dzaima.ui.gui.Font;
import dzaima.ui.gui.config.GConfig;
import dzaima.ui.node.types.editable.EditNode;
import dzaima.ui.node.types.editable.code.langs.*;
import dzaima.utils.Pair;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.paragraph.*;

public class MDLang extends Lang {
  public final GConfig gc;
  public MDLang(ChatMain m, ChatTextArea ta) {
    super(new MDState(m, ta));
    gc = m.gc;
  }
  
  protected TextStyle[] genStyles(Font f) {
    TextStyle[] styles = new TextStyle[39];
    int defTextCol = gc.getProp("str.color").col();
    int spoilerCol = gc.getProp("chat.preview.spoilerBg").col();
    int codeCol = gc.getProp("chat.preview.codeBg").col(); // TODO separate bgInline and bgBlock
    int escCol = gc.getProp("chat.preview.escape").col();
    String codeFamily = gc.getProp("chat.preview.codeFamily").str();
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
        if (i==MDParser.S_COMMAND) col = gc.getProp("chat.preview.commandCol").col();
        if (i==MDParser.S_CODE_ESC || i==MDParser.S_DEF_ESC) col = escCol;
        if (i==MDParser.S_LINK) col = gc.getProp("chat.link.col").col();
        if (i==MDParser.S_QUOTE) col = gc.getProp("chat.preview.quoteCol").col();
        if (i==MDParser.S_QUOTE_LEAD) {
          bgCol = gc.getProp("chat.preview.quoteLeadBg").col();
          col = gc.getProp("chat.preview.quoteLeadCol").col();
        }
      }
      if (bgCol!=0) s.setBackground(new Paint().setColor(bgCol));
      s.setFontFamily(family);
      s.setColor(col);
      styles[i] = s;
    }
    return styles;
  }
  
  static class MDState extends LangState<MDState> {
    private final ChatMain m;
    private final ChatTextArea ta;
    MDState(ChatMain m, ChatTextArea ta) {
      this.ta = ta;
      this.m = m;
    }
    public MDState after(int sz, char[] p, byte[] b) {
      String s = ta.getAll();
      Chatroom r = m.room();
      Pair<Boolean, Integer> h = r==null? new Pair<>(false,0) : r.highlight(s);
      int[] styles = h.a? MDParser.eval(s, c->"").styles : new int[s.length()];
      for (int i = 0; i < h.b; i++) styles[i] = MDParser.S_COMMAND;
      int cx = 0;
      for (EditNode.Line l : ta.lns) {
        int lsz = l.sz();
        byte[] bs = l.st.arr;
        for (int i = 0; i < lsz; i++) {
          bs[i] = (byte)styles[cx+i];
        }
        l.clearPara();
        cx+= lsz+1;
      }
      return this;
    }
    public boolean equals(Object obj) { return obj instanceof MDState; }
    public int hashCode() { return 0; }
  }
  
  public static Lang makeLanguage(ChatMain m, ChatTextArea ta) {
    return new MDLang(m, ta);
  }
}
