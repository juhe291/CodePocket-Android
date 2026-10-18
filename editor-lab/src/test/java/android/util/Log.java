package android.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** JVM test logging bridge only. Real TM4E/Joni still run; no other Android API is replaced.
 * Never packaged in the APK. Preserve warnings/errors rather than enabling returnDefaultValues.
 */
public final class Log {
    public static final List<String> entries = new CopyOnWriteArrayList<>();
    private static int write(String level,String tag,String message,Throwable error) {
        String line=level+"/"+tag+": "+message;entries.add(line);System.err.println(line);
        if(error!=null) error.printStackTrace(System.err);
        return line.length();
    }
    public static int d(String tag,String message) {return write("D",tag,message,null);}
    public static int i(String tag,String message) {return write("I",tag,message,null);}
    public static int v(String tag,String message) {return write("V",tag,message,null);}
    public static int w(String tag,String message) {return write("W",tag,message,null);}
    public static int w(String tag,String message,Throwable error) {return write("W",tag,message,error);}
    public static int e(String tag,String message) {return write("E",tag,message,null);}
    public static int e(String tag,String message,Throwable error) {return write("E",tag,message,error);}
}
