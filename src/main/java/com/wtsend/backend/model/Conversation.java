package com.wtsend.backend.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.wtsend.backend.model.enums.ConversationType;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Conversation implements Serializable {

	@OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL)
	@Builder.Default
	List<Participant> participants = new ArrayList<>();

	@OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL)
	@Builder.Default
	List<Message> messages = new ArrayList<>();

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "lastMessageId")
	Message lastMessage;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id;
	@Enumerated
	ConversationType type;

	/**
	 * For DIRECT conversations: the two participant ids ordered
	 * lexicographically and joined, so (A,B) and (B,A) produce the same value.
	 * NULL for GROUP. A unique index on this column is what actually prevents a
	 * pair ending up with two threads -- the application check alone loses the
	 * race between two concurrent first messages.
	 */
	String directKey;

	@OneToOne(mappedBy = "conversation", cascade = CascadeType.ALL)
	GroupInfo group;

	Instant lastMessageAt;

	@Column(name = "createdAt")
	@CreatedDate
	Instant createdAt;

	@Column(name = "updatedAt")
	@LastModifiedDate
	Instant updatedAt;

	/** Must agree with the SQL backfill in V2: ids are lowercase UUIDs. */
	public static String directKeyOf(String userId, String otherUserId) {
		return userId.compareTo(otherUserId) <= 0
				? userId + ":" + otherUserId
				: otherUserId + ":" + userId;
	}

}
