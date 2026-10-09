package com.wtsend.backend.socket;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SocketIOServerRunner implements CommandLineRunner, DisposableBean {
	private final SocketIOServer server;

	@Override
	public void run(String... args) throws Exception {
		log.info("Starting socket.io server...");
		server.start();
	}

	@Override
	public void destroy() throws Exception {
		// Drain before stopping. server.stop() closes the channels without running
		// the disconnect handlers, so this instance's users would stay in Redis
		// presence -- shown online to everyone else -- until their heartbeat aged
		// out 90 seconds later. Disconnecting runs the normal path: presence
		// removed, presence-changed broadcast, client reconnects to another
		// instance.
		server.getAllClients().forEach(SocketIOClient::disconnect);
		server.stop();
		log.info("Socket.IO server stopped.");
	}

}
