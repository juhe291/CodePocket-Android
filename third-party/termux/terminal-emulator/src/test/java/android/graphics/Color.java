package android.graphics;
/** JVM-only channel extraction; no View, renderer or Android input is mocked. */
public final class Color {
 public static int red(int c){return (c>>>16)&255;}
 public static int green(int c){return (c>>>8)&255;}
 public static int blue(int c){return c&255;}
}
