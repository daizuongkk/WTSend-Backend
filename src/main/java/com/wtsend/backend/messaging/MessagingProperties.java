package com.wtsend.backend.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Topic names and shapes, so partition and replica counts differ by
 * environment without the code knowing which one it is.
 *
 * <p>
 * The topic name carries its major version (`...v1`). With JSON and no schema
 * registry there is nothing to stop an incompatible payload from reaching an
 * old consumer, so a breaking change means a new topic and a window where both
 * are consumed, not an edit to this one.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

	private Topic emailNotifications = new Topic();

	@Data
	public static class Topic {
		private String name;
		private int partitions = 1;
		private short replicas = 1;

		/** Dead letters for this topic, by the convention the error handler uses. */
		public String dltName() {
			return name + ".dlt";
		}
	}
}
