package com.wtsend.backend.socket;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

class SocketIOServerRunnerTest {

	@Test
	void shutdownDisconnectsClientsBeforeStoppingTheServer() throws Exception {
		SocketIOServer server = mock(SocketIOServer.class);
		SocketIOClient first = mock(SocketIOClient.class);
		SocketIOClient second = mock(SocketIOClient.class);
		when(server.getAllClients()).thenReturn(List.of(first, second));

		new SocketIOServerRunner(server).destroy();

		// Order is the whole point. stop() closes the channels without running
		// the disconnect handlers, so a stop that came first would leave this
		// instance's users in Redis presence -- online to everyone else -- until
		// their heartbeat aged out 90 seconds later.
		InOrder order = inOrder(first, second, server);
		order.verify(first).disconnect();
		order.verify(second).disconnect();
		order.verify(server).stop();
	}
}
