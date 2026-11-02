import java.io.FileOutputStream;

/** Independent JVM, used only to prove the receipt lock crosses process boundaries. */
class GitPushLedgerLockHolder {
    public static void main(String[] args) throws Exception {
        try (var channel = new FileOutputStream(args[0], true).getChannel();
             var lock = channel.lock()) {
            System.out.println("locked");
            System.out.flush();
            if (System.in.read() < 0) throw new IllegalStateException("release signal missing");
        }
    }
}
