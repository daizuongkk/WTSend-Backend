package com.wtsend.backend.model;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "messages")
public class Message implements Serializable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)

	private Long id;

	@ManyToOne
	@JoinColumn(name = "senderId")
	private User sender;

	@ManyToOne
	@JoinColumn(name = "conversationId")
	private Conversation conversation;

	@Column(columnDefinition = "TEXT")
	private String content;

	private String imgUrl;

	@Column(name = "createdAt")
	@CreatedDate
	private Instant createdAt;

	@Column(name = "updatedAt")
	@LastModifiedDate
	private Instant updatedAt;
}
