package com.example.netlab.echo;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * 블로킹 IO 에코 서버. 연결당 한 스레드.
 * 학습 포인트:
 *  - Berkeley 소켓 API (socket→bind→listen→accept→read/write→close)
 *  - try-with-resources로 fd 누수 방지
 *  - SoTimeout으로 half-open 회피
 *  - 스레드 풀의 한계
 */
public class EchoServerBlocking {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8090;
        int poolSize = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port), 128);
        System.out.println("[blocking] listening on " + port + " (pool=" + poolSize + ")");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.close(); pool.shutdown(); } catch (IOException ignored) {}
        }));

        while (!server.isClosed()) {
            Socket client = server.accept();
            pool.submit(() -> handle(client));
        }
    }

    static void handle(Socket client) {
        String remote = client.getRemoteSocketAddress().toString();
        try (client;
             BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream()), true)) {

            client.setSoTimeout(60_000);
            client.setTcpNoDelay(true);
            String line;
            while ((line = in.readLine()) != null) {
                out.println("ECHO: " + line);
            }
        } catch (SocketTimeoutException e) {
            System.out.println("[blocking] timeout: " + remote);
        } catch (SocketException e) {
            // RST 등 정상 케이스
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
