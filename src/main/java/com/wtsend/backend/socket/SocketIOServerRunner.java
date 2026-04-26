package com.wtsend.backend.socket;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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
		server.stop();
		log.info("Socket.IO server stopped.");
	}

}
