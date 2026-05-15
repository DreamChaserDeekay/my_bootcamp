package com.example.netlab.echo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

/**
 * Java NIO 에코 서버 — 단일 스레드 selector.
 * 학습 포인트:
 *  - Selector + Channel + Buffer
 *  - 논블로킹 + epoll/IOCP
 *  - selectedKeys()의 it.remove() 누락 함정
 *  - read() == -1 = EOF 처리
 */
public class EchoServerNio {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8091;

        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(port));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("[nio] listening on " + port);

        ByteBuffer buf = ByteBuffer.allocate(4096);

        while (true) {
            selector.select();
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if (!key.isValid()) continue;

                try {
                    if (key.isAcceptable()) onAccept(selector, key);
                    if (key.isReadable())   onRead(buf, key);
                } catch (IOException e) {
                    safeClose(key);
                }
            }
        }
    }

    private static void onAccept(Selector selector, SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        client.configureBlocking(false);
        client.socket().setTcpNoDelay(true);
        client.register(selector, SelectionKey.OP_READ);
        System.out.println("[nio] accepted " + client.getRemoteAddress());
    }

    private static void onRead(ByteBuffer buf, SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        buf.clear();
        int n = client.read(buf);
        if (n == -1) {
            System.out.println("[nio] closed " + client.getRemoteAddress());
            client.close();
            return;
        }
        buf.flip();
        ByteBuffer reply = ByteBuffer.allocate(buf.remaining() + 6);
        reply.put("ECHO: ".getBytes());
        reply.put(buf);
        reply.flip();
        while (reply.hasRemaining()) {
            client.write(reply);
        }
    }

    private static void safeClose(SelectionKey key) {
        try { key.channel().close(); } catch (IOException ignored) {}
    }
}
