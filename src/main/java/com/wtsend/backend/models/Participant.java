package com.wtsend.backend.models;

import java.io.Serializable;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Table(name = "participants", uniqueConstraints = @UniqueConstraint(columnNames = { "conversationId", "userId" }))
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Participant implements Serializable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id;

	@ManyToOne
	@JoinColumn(name = "conversationId")
	private Conversation conversation;

	@ManyToOne
	@JoinColumn(name = "userId")
	User user;

	Instant lastReadAt;
	Long unreadCounts;
	Instant joinedAt;
	@ManyToOne
	@JoinColumn(name = "lastSeenMessageId")
	Message lastSeenMessage;

	@Column(name = "createdAt")
	@CreatedDate
	Instant createdAt;

	@Column(name = "updatedAt")
	@LastModifiedDate
	Instant updatedAt;
}
