package com.example.netlab.chat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.nio.charset.StandardCharsets;

/**
 * Netty 기반 채팅 서버.
 * 학습 포인트:
 *  - boss/worker EventLoopGroup
 *  - ChannelPipeline + Handler 체인
 *  - ChannelGroup으로 브로드캐스트
 *  - EventLoop에서 블로킹 금지 (이 핸들러는 모두 비블로킹)
 */
public class ChatServer {
    static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static void main(String[] args) throws InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8081;
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 128)
             .childOption(ChannelOption.SO_KEEPALIVE, true)
             .childOption(ChannelOption.TCP_NODELAY, true)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new LineBasedFrameDecoder(1024));
                     p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                     p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                     p.addLast(new ChatHandler());
                 }
             });
            ChannelFuture f = b.bind(port).sync();
            System.out.println("[chat] listening on " + port);
            f.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    static class ChatHandler extends SimpleChannelInboundHandler<String> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channels.add(ctx.channel());
            broadcast("[" + ctx.channel().remoteAddress() + " joined]");
        }
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            broadcast("[" + ctx.channel().remoteAddress() + " left]");
        }
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            broadcast(ctx.channel().remoteAddress() + ": " + msg);
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
        private void broadcast(String msg) {
            for (Channel ch : channels) {
                ch.writeAndFlush(msg + "\n");
            }
        }
    }
}
