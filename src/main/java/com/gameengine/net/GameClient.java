package com.gameengine.net;

import com.gameengine.config.GameConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * NIO 客户端。
 *
 * 线程模型：
 * - reader 线程：阻塞读，负责行切分与消息分发（只写 volatile 字段，不碰游戏对象）。
 * - writer 线程：唯一写线程，所有上行消息经无锁队列排队，避免多线程写同一 Channel
 *   导致 TCP 字节流交错、消息损坏。
 */
public final class GameClient {
    private SocketChannel channel;
    private final BlockingQueue<byte[]> outbox = new ArrayBlockingQueue<>(512);
    private final CountDownLatch joinLatch = new CountDownLatch(1);

    private volatile String latestState;
    private volatile String sessionId;
    private volatile String lastError;
    private volatile boolean connected;

    private Thread readerThread;
    private Thread writerThread;
    private long lastPingTime;

    /**
     * 连接并加入游戏（阻塞直到收到 JOIN_ACK 或超时）。
     */
    public boolean connectAndJoin(String host, int port, String playerName, long timeoutMs) {
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.socket().setTcpNoDelay(true);
            channel.socket().connect(new InetSocketAddress(host, port), (int) Math.min(timeoutMs, 5000));
            connected = true;
        } catch (IOException e) {
            lastError = e.getMessage();
            System.err.println("[Client] 连接 " + host + ":" + port + " 失败: " + e.getMessage());
            close();
            return false;
        }

        readerThread = new Thread(this::readLoop, "client-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        writerThread = new Thread(this::writeLoop, "client-writer");
        writerThread.setDaemon(true);
        writerThread.start();

        boolean queued = sendRaw(Protocol.join(playerName));
        if (!queued) {
            close();
            return false;
        }

        try {
            if (!joinLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                lastError = "join timeout";
                System.err.println("[Client] 加入游戏超时（未收到 JOIN_ACK）");
                close();
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            close();
            return false;
        }

        if (!connected) {
            System.err.println("[Client] 加入失败: " + lastError);
            close();
            return false;
        }

        System.out.println("[Client] 已加入游戏, 会话 " + sessionId);
        return true;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getLastError() {
        return lastError;
    }

    /**
     * 发送一帧输入。队列满时直接丢弃（旧输入本来就会过期，丢帧比阻塞主线程更好）。
     */
    public void sendInput(PlayerCommand command) {
        if (!connected || command == null) return;
        if (!sendRaw(Protocol.input(command))) {
            // 队列满：丢弃最旧的一条再重试一次
            outbox.poll();
            sendRaw(Protocol.input(command));
        }
    }

    private boolean sendRaw(String message) {
        if (!connected) return false;
        return outbox.offer(Protocol.encodeLine(message));
    }

    /**
     * 取出并清空最近一次服务器状态（JSON 字符串）。
     * 主线程每帧调用，天然只保留最新帧，避免状态堆积。
     */
    public String pollState() {
        String state = latestState;
        latestState = null;
        return state;
    }

    public void close() {
        connected = false;
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {}
            channel = null;
        }
        if (readerThread != null) readerThread.interrupt();
        if (writerThread != null) writerThread.interrupt();
    }

    // ==================== 后台线程 ====================

    private void readLoop() {
        ByteArrayOutputStream pending = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        try {
            while (connected) {
                SocketChannel ch = channel;
                if (ch == null) break;
                buffer.clear();
                int n = ch.read(buffer);
                if (n < 0) {
                    break;
                }
                if (n == 0) continue;

                buffer.flip();
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);

                if (pending.size() + chunk.length > GameConfig.NETWORK_MAX_LINE_LENGTH * 4) {
                    lastError = "server sent too much data";
                    break;
                }
                pending.write(chunk, 0, chunk.length);

                byte[] data = pending.toByteArray();
                int start = 0;
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == '\n') {
                        int len = i - start;
                        if (len > 0 && len <= GameConfig.NETWORK_MAX_LINE_LENGTH) {
                            String line = new String(data, start, len, StandardCharsets.UTF_8).trim();
                            if (!line.isEmpty()) {
                                dispatch(line);
                            }
                        }
                        start = i + 1;
                    }
                }
                pending.reset();
                if (start < data.length) {
                    pending.write(data, start, data.length - start);
                }
            }
        } catch (IOException e) {
            if (connected) {
                lastError = e.getMessage();
            }
        } finally {
            connected = false;
            joinLatch.countDown(); // 避免主线程一直等待
        }
    }

    private void dispatch(String line) {
        int colon = line.indexOf(':');
        String command = colon < 0 ? line : line.substring(0, colon);
        String payload = colon < 0 ? "" : line.substring(colon + 1);

        switch (command) {
            case Protocol.CMD_WELCOME:
                sessionId = payload;
                break;
            case Protocol.CMD_JOIN_ACK:
                joinLatch.countDown();
                break;
            case Protocol.CMD_STATE:
                latestState = payload;
                break;
            case Protocol.CMD_PONG:
                break;
            case Protocol.CMD_PLAYER_LEFT:
                break;
            case Protocol.CMD_ERROR:
                lastError = payload;
                System.err.println("[Client] 服务器错误: " + payload);
                break;
            default:
                break;
        }
    }

    private void writeLoop() {
        try {
            while (connected) {
                byte[] message = outbox.poll(500, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();

                if (message == null) {
                    if (now - lastPingTime >= GameConfig.NETWORK_PING_INTERVAL_MS) {
                        writeFully(Protocol.encodeLine(Protocol.ping()));
                        lastPingTime = now;
                    }
                    continue;
                }

                writeFully(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (connected) {
                lastError = e.getMessage();
            }
        } finally {
            connected = false;
        }
    }

    private void writeFully(byte[] bytes) throws IOException {
        SocketChannel ch = channel;
        if (ch == null) throw new IOException("channel closed");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            ch.write(buffer);
        }
    }
}
