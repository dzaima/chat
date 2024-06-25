package chat;

import java.util.function.*;

public abstract class Command {
  public final String name;
  public final boolean hasArgs;
  
  public Command(String name, boolean hasArgs) {
    this.name = name;
    this.hasArgs = hasArgs;
  }
  
  public abstract Object run(String s); // false result means not accepted; true means okay; everything else is target-specific
  
  public static class SimpleTestCommand extends Command {
    private final Function<String, Boolean> run;
    public SimpleTestCommand(String name, Function<String, Boolean> run) {
      super(name, true);
      this.run = run;
    }
    public Object run(String s) { return run.apply(s); }
  }
  public static class SimpleArgCommand extends Command {
    private final Consumer<String> run;
    public SimpleArgCommand(String name, Consumer<String> run) {
      super(name, true);
      this.run = run;
    }
    public Object run(String s) { run.accept(s); return true; }
  }
  
  public static class SimplePlainCommand extends Command {
    private final Runnable run;
    public SimplePlainCommand(String name, Runnable run) {
      super(name, false);
      this.run = run;
    }
    public Object run(String s) { run.run(); return true; }
  }
}
