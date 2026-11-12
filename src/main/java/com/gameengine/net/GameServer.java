package com.gameengine.net;

import com.gameengine.config.GameConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NIO 游戏服务器（单线程 Selector）。
 *
 * 线程模型：
 * - 网络线程只负责收发字节、维护会话，绝不触碰 Scene / GameObject。
 * - 游戏线程通过 {@link #pollEvents()} 取出 JOIN/INPUT/LEFT 事件并在游戏线程处理，
 *   通过 {@link #broadcastState(String)} 投递状态字符串（内部只入队，不阻塞）。
 *
 * 这解决了旧实现中"网络线程并发遍历场景导致 ConcurrentModificationException"
 * 以及"在 Selector 线程里阻塞写导致整个服务器卡死"的问题。
 */
public final class GameServer {
    public enum EventType { JOIN, LEFT, INPUT }

    /** 网络事件：由游戏线程消费 */
    public static final class Event {
        public final EventType type;
        public final String sessionId;
        public final String playerName;
        public final PlayerCommand command;

        private Event(EventType type, String sessionId, String playerName, PlayerCommand command) {
            this.type = type;
            this.sessionId = sessionId;
            this.playerName = playerName;
            this.command = command;
        }

        public static Event join(String sessionId, String name) {
            return new Event(EventType.JOIN, sessionId, name, null);
        }

        public static Event left(String sessionId) {
            return new Event(EventType.LEFT, sessionId, null, null);
        }

        public static Event input(String sessionId, PlayerCommand command) {
            return new Event(EventType.INPUT, sessionId, null, command);
        }
    }

    private static final class ClientConnection {
        final SocketChannel channel;
        final SelectionKey key;
        final String sessionId;
        final ByteBuffer readBuffer = ByteBuffer.allocate(8192);
        final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        final Queue<ByteBuffer> outbox = new ConcurrentLinkedQueue<>();

        String playerName = "";
        boolean joined;
        volatile long lastSeen = System.currentTimeMillis();
        long rateWindowStart = System.currentTimeMillis();
        int rateCount;

        ClientConnection(SocketChannel channel, SelectionKey key, String sessionId) {
            this.channel = channel;
            this.key = key;
            this.sessionId = sessionId;
        }

        void enqueueLine(String message) {
            enqueueBytes(Protocol.encodeLine(message));
        }

        void enqueueBytes(byte[] bytes) {
            if (outbox.size() > 512) {
                // 客户端读取过慢：丢弃旧帧，避免服务器内存无上限增长
                outbox.poll();
            }
            outbox.add(ByteBuffer.wrap(bytes));
        }
    }

    private final int port;
    private final Map<SocketChannel, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Queue<Event> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger sessionSeq = new AtomicInteger(1);

    private volatile boolean running;
    private volatile Selector selector;
    private ServerSocketChannel serverChannel;
    private Thread thread;

    public GameServer(int port) {
        this.port = port;
    }

    /** @return 是否成功绑定端口 */
    public boolean start() {
        if (running) return true;
        try {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().setReuseAddress(true);
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            System.err.println("[Server] 无法监听端口 " + port + ": " + e.getMessage());
            closeQuietly();
            return false;
        }

        running = true;
        thread = new Thread(this::runLoop, "game-nio-server");
        thread.setDaemon(true);
        thread.start();
        System.out.println("[Server] 游戏服务器已启动，端口 " + port);
        return true;
    }

    public void stop() {
        if (!running) return;
        running = false;
        Selector sel = selector;
        if (sel != null) sel.wakeup();
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeQuietly();
    }

    private synchronized void closeQuietly() {
        for (ClientConnection conn : connections.values()) {
            closeConnection(conn, false);
        }
        connections.clear();
        try {
            if (serverChannel != null) serverChannel.close();
        } catch (IOException ignored) {}
        try {
            if (selector != null) selector.close();
        } catch (IOException ignored) {}
        serverChannel = null;
        selector = null;
    }

    /**
     * 广播一帧状态。可在任意线程调用：只做入队 + 唤醒 Selector。
     */
    public void broadcastState(String json) {
        if (json == null || json.isEmpty() || !running) return;
        byte[] payload = Protocol.encodeLine(Protocol.state(json));
        for (ClientConnection conn : connections.values()) {
            if (conn.joined) {
                conn.enqueueBytes(payload);
            }
        }
        Selector sel = selector;
        if (sel != null) sel.wakeup();
    }

    /**
     * 取出自上次调用以来的所有事件（由游戏线程调用）。
     */
    public List<Event> pollEvents() {
        List<Event> result = new ArrayList<>();
        Event event;
        while ((event = events.poll()) != null) {
            result.add(event);
        }
        return result;
    }

    public int getClientCount() {
        int count = 0;
        for (ClientConnection conn : connections.values()) {
            if (conn.joined) count++;
        }
        return count;
    }

    // ==================== 网络线程 ====================

    private void runLoop() {
        try {
            while (running) {
                selector.select(10);
                processKeys();
                updateInterestOps();
                expireIdleClients();
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[Server] 网络线程异常: " + e.getMessage());
            }
        } finally {
            closeQuietly();
        }
    }

    private void processKeys() {
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            if (!key.isValid()) continue;

            Object attachment = key.attachment();
            ClientConnection conn = attachment instanceof ClientConnection ? (ClientConnection) attachment : null;

            try {
                if (key.isAcceptable()) {
                    acceptConnection();
                } else if (conn != null) {
                    if (key.isReadable()) {
                        readFrom(conn);
                    }
                    if (key.isValid() && key.isWritable()) {
                        flush(conn);
                    }
                }
            } catch (Exception e) {
                if (conn != null) {
                    closeConnection(conn, true);
                } else {
                    key.cancel();
                }
            }
        }
    }

    private void acceptConnection() throws IOException {
        SocketChannel channel = serverChannel.accept();
        if (channel == null) return;

        if (connections.size() >= GameConfig.NETWORK_MAX_CLIENTS) {
            ByteBuffer busy = ByteBuffer.wrap(Protocol.encodeLine(Protocol.error("server full")));
            try {
                channel.write(busy);
            } catch (IOException ignored) {}
            channel.close();
            return;
        }

        channel.configureBlocking(false);
        channel.socket().setTcpNoDelay(true);

        String sessionId = "S" + sessionSeq.getAndIncrement() + "_" + UUID.randomUUID().toString().substring(0, 6);
        SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
        ClientConnection conn = new ClientConnection(channel, key, sessionId);
        key.attach(conn);
        connections.put(channel, conn);

        conn.enqueueLine(Protocol.welcome(sessionId));
        selector.wakeup();
        System.out.println("[Server] 客户端已连接: " + sessionId);
    }

    private void readFrom(ClientConnection conn) throws IOException {
        conn.readBuffer.clear();
        int n = conn.channel.read(conn.readBuffer);
        if (n < 0) {
            closeConnection(conn, true);
            return;
        }
        if (n == 0) return;

        conn.lastSeen = System.currentTimeMillis();
        conn.readBuffer.flip();
        byte[] chunk = new byte[conn.readBuffer.remaining()];
        conn.readBuffer.get(chunk);

        // 防御：单个客户端不消费数据时，缓冲不能无限增长
        if (conn.pending.size() + chunk.length > GameConfig.NETWORK_MAX_LINE_LENGTH * 2) {
            closeConnection(conn, true);
            return;
        }
        conn.pending.write(chunk, 0, chunk.length);

        // 按字节切分 '\n'（UTF-8 的多字节序列内不会出现 0x0A）
        byte[] data = conn.pending.toByteArray();
        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                int len = i - start;
                if (len > 0 && len <= GameConfig.NETWORK_MAX_LINE_LENGTH) {
                    String line = new String(data, start, len, StandardCharsets.UTF_8).trim();
                    if (!line.isEmpty()) {
                        processLine(conn, line);
                    }
                }
                start = i + 1;
            }
        }

        conn.pending.reset();
        if (start < data.length) {
            conn.pending.write(data, start, data.length - start);
        }
    }

    private void processLine(ClientConnection conn, String line) {
        int colon = line.indexOf(':');
        String command = colon < 0 ? line : line.substring(0, colon);
        String payload = colon < 0 ? "" : line.substring(colon + 1);

        switch (command) {
            case Protocol.CMD_PING:
                conn.enqueueLine(Protocol.pong());
                break;

            case Protocol.CMD_JOIN:
                if (!conn.joined) {
                    conn.joined = true;
                    conn.playerName = Protocol.sanitizeName(payload);
                    conn.enqueueLine(Protocol.joinAck());
                    enqueueEvent(Event.join(conn.sessionId, conn.playerName));
                    System.out.println("[Server] 玩家加入: " + conn.playerName + " (" + conn.sessionId + ")");
                }
                break;

            case Protocol.CMD_INPUT:
                if (!conn.joined) break;
                if (!allowInput(conn)) break;
                PlayerCommand cmd = Protocol.parseInput(payload);
                if (cmd != null) {
                    enqueueEvent(Event.input(conn.sessionId, cmd));
                }
                break;

            default:
                conn.enqueueLine(Protocol.error("unknown command"));
                break;
        }
    }

    /** 简易限流：每连接每秒最多 120 条输入，防止恶意刷包 */
    private boolean allowInput(ClientConnection conn) {
        long now = System.currentTimeMillis();
        if (now - conn.rateWindowStart >= 1000) {
            conn.rateWindowStart = now;
            conn.rateCount = 0;
        }
        return ++conn.rateCount <= 120;
    }

    private void enqueueEvent(Event event) {
        if (events.size() > 2048) {
            events.poll();
        }
        events.add(event);
    }

    private void flush(ClientConnection conn) throws IOException {
        ByteBuffer buffer;
        while ((buffer = conn.outbox.peek()) != null) {
            conn.channel.write(buffer);
            if (buffer.hasRemaining()) break;
            conn.outbox.poll();
        }
    }

    private void updateInterestOps() {
        for (ClientConnection conn : connections.values()) {
            if (!conn.key.isValid()) continue;
            int ops = SelectionKey.OP_READ;
            if (!conn.outbox.isEmpty()) {
                ops |= SelectionKey.OP_WRITE;
            }
            if (conn.key.interestOps() != ops) {
                conn.key.interestOps(ops);
            }
        }
    }

    private void expireIdleClients() {
        long now = System.currentTimeMillis();
        for (ClientConnection conn : connections.values()) {
            if (now - conn.lastSeen > GameConfig.NETWORK_TIMEOUT_MS) {
                closeConnection(conn, true);
            }
        }
    }

    private void closeConnection(ClientConnection conn, boolean notify) {
        if (conn == null) return;
        connections.remove(conn.channel, conn);
        try {
            conn.key.cancel();
        } catch (Exception ignored) {}
        try {
            conn.channel.close();
        } catch (IOException ignored) {}

        if (notify && conn.joined) {
            enqueueEvent(Event.left(conn.sessionId));
            System.out.println("[Server] 客户端断开: " + conn.sessionId);
        }
    }
}
