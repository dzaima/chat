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

import java.util.Arrays;

public class MDLang extends Lang.GloballyHighlightedLang {
  public final GConfig gc;
  public final ChatMain m;
  public MDLang(ChatMain m) {
    gc = m.gc;
    this.m = m;
  }
  
  public byte[] globalHighlight(String s) {
    Chatroom r = m.room();
    if (r==null) return new byte[s.length()];
    Pair<Boolean, Integer> h = r.highlight(s);
    byte[] styles = h.a? MDParser.eval(s, c -> "").styles : new byte[s.length()];
    for (int i = 0; i < h.b; i++) styles[i] = MDParser.S_COMMAND;
    return styles;
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
  
  public static Lang makeLanguage(ChatMain m) {
    return new MDLang(m);
  }
}
