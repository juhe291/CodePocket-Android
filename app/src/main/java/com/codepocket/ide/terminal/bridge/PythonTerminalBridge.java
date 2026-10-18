package com.codepocket.ide.terminal.bridge;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Real foreground child of /system/bin/sh. Transfers its actual PTY stdio, then waits for Python exit. */
public final class PythonTerminalBridge {
    public static void main(String[] args) {
        int status = 125;
        try (LocalSocket socket = new LocalSocket()) {
            if (args.length != 2) throw new IllegalArgumentException("Missing bridge identity");
            socket.connect(new LocalSocketAddress(args[0], LocalSocketAddress.Namespace.ABSTRACT));
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            socket.setFileDescriptorsForSend(new FileDescriptor[]{FileDescriptor.in, FileDescriptor.out, FileDescriptor.err});
            out.writeByte(67); out.flush();
            socket.setFileDescriptorsForSend(null);
            out.writeInt(1); out.writeUTF(args[1]); out.flush();
            status = new DataInputStream(socket.getInputStream()).readInt();
        } catch (Exception failure) {
            // RuntimeInit may redirect System.err. The inherited descriptor remains the actual terminal.
            try {
                FileOutputStream error = new FileOutputStream(FileDescriptor.err);
                error.write(("CodePocket Python bridge: " + failure.getClass().getSimpleName() + ": " + failure.getMessage() + "\r\n").getBytes(StandardCharsets.UTF_8));
                error.flush();
            } catch (Exception ignored) { }
        }
        System.exit(status >= 0 && status <= 255 ? status : 1);
    }
}
