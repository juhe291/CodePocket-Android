package android.util;
/** JVM-only equivalents for the Base64 operations used by upstream core tests. */
public final class Base64 {
 public static byte[] decode(String value,int flags) { return java.util.Base64.getMimeDecoder().decode(value); }
 public static String encodeToString(byte[] value,int flags) { return java.util.Base64.getMimeEncoder(76,new byte[]{10}).encodeToString(value)+"\n"; }
}
