package com.wtsend.backend.socket;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Identifies this process among the instances sharing one Redis.
 *
 * <p>
 * Two things need it. {@link SocketRelay} stamps every published envelope with
 * it so the publishing node can skip its own message instead of emitting it
 * twice, and {@link RedisPresenceStore} prefixes socket ids with it so entries
 * left behind by a crashed instance are recognisable as such.
 *
 * <p>
 * A bean rather than {@code @Value} at each use site: the default contains a
 * random component, and {@code ${random.uuid}} in a properties file is
 * re-evaluated per lookup, so two injection points would disagree about which
 * instance this is.
 */
@Component
public class InstanceId {

	private final String value;

	InstanceId(@Value("${app.instance-id:}") String configured) {
		this.value = (configured == null || configured.isBlank()) ? generate() : configured;
	}

	public String value() {
		return value;
	}

	private static String generate() {
		return hostname() + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	private static String hostname() {
		String fromEnv = System.getenv("HOSTNAME");
		if (fromEnv == null || fromEnv.isBlank())
			fromEnv = System.getenv("COMPUTERNAME");
		if (fromEnv != null && !fromEnv.isBlank())
			return fromEnv;
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "unknown";
		}
	}
}
