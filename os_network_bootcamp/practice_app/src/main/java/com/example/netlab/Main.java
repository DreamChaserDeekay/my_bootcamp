package com.example.netlab;

import com.example.netlab.echo.EchoServerBlocking;
import com.example.netlab.echo.EchoServerNio;
import com.example.netlab.chat.ChatServer;
import com.example.netlab.client.ClientRest;
import com.example.netlab.client.ClientPool;
import com.example.netlab.client.ClientWebFlux;

import org.springframework.boot.SpringApplication;

public class Main {
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "spring";
        switch (mode) {
            case "echo-blocking" -> EchoServerBlocking.main(rest(args));
            case "echo-nio"      -> EchoServerNio.main(rest(args));
            case "chat"          -> ChatServer.main(rest(args));
            case "client-restt"  -> ClientRest.main(rest(args));
            case "client-pool"   -> ClientPool.main(rest(args));
            case "client-webc"   -> ClientWebFlux.main(rest(args));
            case "spring"        -> SpringApplication.run(NetLabApp.class, rest(args));
            default -> {
                System.err.println("Unknown mode: " + mode);
                System.err.println("Usage: java Main {echo-blocking|echo-nio|chat|client-restt|client-pool|client-webc|spring}");
                System.exit(1);
            }
        }
    }

    private static String[] rest(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] r = new String[args.length - 1];
        System.arraycopy(args, 1, r, 0, r.length);
        return r;
    }
}
