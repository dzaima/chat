package dzaima.utils.options;

import dzaima.ui.gui.Windows;
import dzaima.ui.gui.jwm.JWMWindow;
import dzaima.utils.*;
import io.github.humbleui.jwm.skija.*;

import java.util.*;
import java.util.function.Consumer;

public class AutoOptions {
  public final Options o;
  private boolean ran;
  
  private int maxLeft = 0;
  private final HashMap<String, Options.ArgFn> shortArgs = new HashMap<>();
  private final HashMap<String, Options.ArgFn> longArgs = new HashMap<>();
  private final HashSet<String> maxOne = new HashSet<>();
  private final Vec<Pair<String, String>> helpTexts = new Vec<>();
  
  public AutoOptions() {
    o = new Options();
  }
  
  private void errorUnknown(String arg) {
    Options.error("Unknown argument '" + arg + "'");
  }
  private void errorRepeated(String arg) {
    Options.error("Repeated argument '" + arg + "'");
  }
  
  public Vec<String> run(String[] args) {
    ran = true;
    Options.ArgFn longFn = null;
    HashSet<String> encountered = new HashSet<>();
    if (longArgs.size()>0) longFn = (arg, i, get) -> {
      if (maxOne.contains(arg) && !encountered.add(arg)) errorRepeated(arg);
      Options.ArgFn fn = longArgs.get(arg);
      if (fn==null) errorUnknown(arg);
      else fn.run(arg, i, get);
    };
    Options.ArgFn shortFn = null;
    if (shortArgs.size()>0) shortFn = (arg, i, get) -> {
      if (maxOne.contains(arg) && !encountered.add(arg)) errorRepeated(arg);
      Options.ArgFn fn = shortArgs.get(arg);
      if (fn==null) errorUnknown(arg);
      else fn.run(arg, i, get);
    };
    Vec<String> left = o.process(args, longFn, shortFn);
    
    if (left.sz > maxLeft) Options.error(maxLeft==0? "Didn't expect any non-flag arguments" : "Expected at most " + maxLeft + " plain argument");
    
    return left;
  }
  
  public void autoHelp() {
    autoHelp(true, true);
  }
  public void autoHelp(boolean shortDashH, boolean shortDashQuestionMark) {
    assert !ran;
    Options.ArgFn fn = (arg, i, get) -> {
      System.out.println("Options:");
      for (Pair<String, String> p : helpTexts) {
        System.out.println("  "+p.a+"  "+p.b);
      }
      System.exit(0);
    };
    longArgs.put("--help", fn);
    if (shortDashH) shortArgs.put("-h", fn);
    if (shortDashQuestionMark) shortArgs.put("-?", fn);
  }
  
  public void putArg(String arg, Options.ArgFn fn) {
    HashMap<String, Options.ArgFn> list = arg.startsWith("--")? longArgs : shortArgs;
    assert !list.containsKey(arg);
    list.put(arg, fn);
  }
  public void limitArgOne(String arg) {
    maxOne.add(arg);
  }
  
  public void addHelpText(String arg, String text) {
    assert !ran;
    helpTexts.add(new Pair<>(arg, text));
  }
  
  public void argBool(String a, String helpText) {
    addHelpText(a, helpText);
    putArg(a, (arg, i, get) -> o.put(arg, i, "true"));
  }
  
  public void argBoolRun(String a, String helpText, Runnable onSet) {
    limitArgOne(a);
    addHelpText(a, helpText);
    putArg(a, (arg, i, get) -> onSet.run());
  }
  
  public void argString(String a, String helpText) {
    addHelpText(a+"=…", helpText);
    putArg(a, (arg, i, get) -> o.put(arg, i, get.get()));
  }
  
  public void argStringRunOne(String a, String helpText, Consumer<String> take) {
    limitArgOne(a);
    stringRun(a, helpText, take);
  }
  public void stringRun(String a, String helpText, Consumer<String> take) {
    addHelpText(a, helpText);
    longArgs.put(a, (arg, i, get) -> take.accept(get.get()));
  }
  
  public void acceptLeft() {
    assert !ran;
    acceptLeft(Integer.MAX_VALUE);
  }
  public void acceptLeft(int maxCount) {
    assert !ran;
    maxLeft = maxCount;
  }
  
  public void autoDebug(Log.Level defaultLevel) {
    if (Log.level != defaultLevel) {
      Log.setLogLevel(defaultLevel);
    }
    argStringRunOne("--log", "Logging level: fine, info, warn, error (default: "+defaultLevel.toString().toLowerCase()+")", s -> {
      switch (s) {
        case "fine": Log.setLogLevel(Log.Level.FINE); break;
        case "info": Log.setLogLevel(Log.Level.INFO); break;
        case "warn": Log.setLogLevel(Log.Level.WARN); break;
        case "error": Log.setLogLevel(Log.Level.ERROR); break;
        default: Options.error("--log: invalid mode");
      }
    });
    argStringRunOne("--window-manager", "Manager: LWJGL or JWM", s -> {
      switch (s.toUpperCase()) {
        case "JWM": Windows.setManager(Windows.Manager.JWM); break;
        case "LWJGL": Windows.setManager(Windows.Manager.LWJGL); break;
        default: Options.error("--window-manager: invalid value");
      }
    });
    argStringRunOne("--jwm-backend", "Skija backend: GL, D3D, metal, or raster", s -> {
      switch (s.toLowerCase()) {
        case "gl": JWMWindow.preferredLayer = w -> new LayerGLSkija(); break;
        case "d3d": JWMWindow.preferredLayer = w -> new LayerD3D12Skija(); break;
        case "metal": JWMWindow.preferredLayer = w -> new LayerMetalSkija(); break;
        case "raster": JWMWindow.preferredLayer = w -> new LayerRasterSkija(); break;
        default: Options.error("--jwm-backend: invalid value");
      }
    });
  }
}
