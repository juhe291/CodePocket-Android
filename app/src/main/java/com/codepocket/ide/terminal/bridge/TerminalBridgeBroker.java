package com.codepocket.ide.terminal.bridge;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.system.Os;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** One authenticated local connection per run. No network port, exported service or Activity retained. */
public final class TerminalBridgeBroker implements Closeable {
    public interface Listener {
        void listening(TerminalBridgeBroker broker);
        void connected(TerminalBridgeBroker broker, Streams streams);
        void disconnected(TerminalBridgeBroker broker, String error);
    }
    public static final class Streams implements Closeable {
        public final ParcelFileDescriptor input, output, error;
        Streams(ParcelFileDescriptor input, ParcelFileDescriptor output, ParcelFileDescriptor error) { this.input=input;this.output=output;this.error=error; }
        @Override public void close() { for (ParcelFileDescriptor fd:new ParcelFileDescriptor[]{input,output,error}) try { fd.close(); } catch(IOException ignored) { } }
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean();
    private final String address = "cp-" + random(16);
    private final String token = random(32);
    private final Listener listener;
    private volatile LocalServerSocket server;
    private volatile LocalSocket channel;
    private TerminalBridgeBroker(Listener listener) { this.listener=listener; }
    public static TerminalBridgeBroker start(Listener listener) {
        TerminalBridgeBroker broker=new TerminalBridgeBroker(listener);
        new Thread(broker::serve,"CodePocket-Terminal-Bridge").start();return broker;
    }
    private static String random(int count) { byte[] bytes=new byte[count];new SecureRandom().nextBytes(bytes);StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return value.toString(); }
    public String command(String apk) { return BridgeCommand.build(apk,address,token); }
    private void serve() {
        try {
            LocalServerSocket listening=new LocalServerSocket(address);server=listening;
            if (closed.get()) { listening.close(); return; }
            main.post(()->{if(!closed.get())listener.listening(this);});
            while(!closed.get()) {
                LocalSocket socket=listening.accept();channel=socket;
                List<ParcelFileDescriptor> copies=new ArrayList<>();FileDescriptor[] received=null;
                boolean accepted=false;
                try {
                    if(socket.getPeerCredentials().getUid()!=Process.myUid())continue;
                    socket.setSoTimeout(10000);
                    DataInputStream in=new DataInputStream(socket.getInputStream());
                    if(in.readUnsignedByte()!=67)throw new IOException("Invalid bridge marker");
                    received=socket.getAncillaryFileDescriptors();
                    if(received==null||received.length!=3)throw new IOException("Missing terminal descriptors");
                    if(in.readInt()!=1||!token.equals(in.readUTF()))throw new IOException("Invalid bridge authentication");
                    for(FileDescriptor fd:received)copies.add(ParcelFileDescriptor.dup(fd));
                    Streams streams=new Streams(copies.get(0),copies.get(1),copies.get(2));copies.clear();
                    accepted=true;socket.setSoTimeout(0);listening.close();
                    main.post(()->{if(closed.get())streams.close();else listener.connected(this,streams);});
                    // The foreground bridge is the peer. Ctrl+C/exit closes this socket and cancels Python.
                    while(in.read()!=-1) { }
                    if(!closed.get())main.post(()->{if(!closed.get())listener.disconnected(this,"终端前台进程已退出");});
                    return;
                } finally {
                    if(received!=null)for(FileDescriptor fd:received)try{Os.close(fd);}catch(Exception ignored){}
                    for(ParcelFileDescriptor fd:copies)try{fd.close();}catch(IOException ignored){}
                    if(!accepted) { socket.close();channel=null; }
                }
            }
        } catch(Exception failure) {
            if(!closed.get())main.post(()->{if(!closed.get())listener.disconnected(this,failure.getClass().getSimpleName()+": "+failure.getMessage());});
            closeHandles();
        }
    }
    public void complete(int status) {
        if(!closed.compareAndSet(false,true))return;
        LocalSocket socket=channel;
        if(socket!=null)try { DataOutputStream out=new DataOutputStream(socket.getOutputStream());out.writeInt(status);out.flush(); }catch(IOException ignored){}
        closeHandles();
    }
    private void closeHandles() {
        LocalSocket socket=channel;channel=null;if(socket!=null)try{socket.close();}catch(IOException ignored){}
        LocalServerSocket listening=server;server=null;if(listening!=null)try{listening.close();}catch(IOException ignored){}
    }
    @Override public void close() { closed.set(true);closeHandles(); }
}
