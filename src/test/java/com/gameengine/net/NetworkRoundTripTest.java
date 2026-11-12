package com.gameengine.net;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端回环测试：真实启动 Selector 服务器与客户端，验证
 * JOIN / INPUT / STATE / 断开 全链路可用。
 *
 * 这类测试能在重构网络层后第一时间发现"协议字段对不上""写线程互相踩"等问题。
 */
class NetworkRoundTripTest {

    private GameServer server;
    private GameClient client;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        port = findFreePort();
        server = new GameServer(port);
        assertTrue(server.start(), "服务器应能绑定端口");
        client = new GameClient();
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    @Test
    @Timeout(10)
    void clientShouldJoinSendInputAndReceiveState() throws Exception {
        assertTrue(client.connectAndJoin("127.0.0.1", port, "Tester", 3000),
                "客户端应收到 JOIN_ACK");
        assertNotNull(client.getSessionId());

        GameServer.Event join = awaitEvent(GameServer.EventType.JOIN);
        assertNotNull(join, "服务器应收到 JOIN 事件");
        assertEquals("Tester", join.playerName);

        client.sendInput(new PlayerCommand(7L, 1f, 0f, true, false, false, 300f, 400f));
        GameServer.Event input = awaitEvent(GameServer.EventType.INPUT);
        assertNotNull(input, "服务器应收到 INPUT 事件");
        assertEquals(7L, input.command.sequence);
        assertTrue(input.command.melee);

        server.broadcastState("{\"type\":\"state\",\"t\":1,\"entities\":[]}");
        String state = awaitState();
        assertNotNull(state, "客户端应收到 STATE");
        assertTrue(state.contains("\"type\":\"state\""));
    }

    @Test
    @Timeout(10)
    void clientShouldBeNotifiedWhenDisconnected() throws Exception {
        assertTrue(client.connectAndJoin("127.0.0.1", port, "Leaver", 3000));
        assertNotNull(awaitEvent(GameServer.EventType.JOIN));

        client.close();
        GameServer.Event left = awaitEvent(GameServer.EventType.LEFT);
        assertNotNull(left, "客户端关闭后服务器应发出 LEFT 事件");
    }

    private GameServer.Event awaitEvent(GameServer.EventType type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            for (GameServer.Event event : server.pollEvents()) {
                if (event.type == type) return event;
            }
            Thread.sleep(10);
        }
        return null;
    }

    private String awaitState() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            String state = client.pollState();
            if (state != null) return state;
            Thread.sleep(10);
        }
        return null;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
