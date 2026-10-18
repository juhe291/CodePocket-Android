package android.util;
/** JVM test log bridge. Never packaged in the APK. */
public final class Log {
 public static int e(String t,String m){System.err.println(t+": "+m);return 0;}
 public static int w(String t,String m){System.err.println(t+": "+m);return 0;}
 public static int i(String t,String m){System.err.println(t+": "+m);return 0;}
 public static int d(String t,String m){System.err.println(t+": "+m);return 0;}
 public static int v(String t,String m){System.err.println(t+": "+m);return 0;}
}
