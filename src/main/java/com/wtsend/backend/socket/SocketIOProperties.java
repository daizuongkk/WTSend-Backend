package com.wtsend.backend.socket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Component
@ConfigurationProperties(prefix = "socketio")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SocketIOProperties {
	String host;
	Integer port;
	Integer bossCount;
	Integer workerCount;
	Boolean allowCustomRequests;
	Integer upgradeTimeout;
	Integer pingTimeout;
	Integer pingInterval;
	Integer maxFramePayloadLength;
	Integer maxHttpContentLength;
	String origin;

}
