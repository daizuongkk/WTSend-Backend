package com.wtsend.backend.models;

import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "users")
public class User implements UserDetails {

	@OneToMany(mappedBy = "user")
	@Builder.Default
	List<Participant> participants = new ArrayList<>();

	@OneToMany(mappedBy = "sender")
	@Builder.Default
	List<Message> messages = new ArrayList<>();

	@OneToMany(mappedBy = "userA")
	@Builder.Default
	List<Friend> friends1 = new ArrayList<>();

	@OneToMany(mappedBy = "userB")
	@Builder.Default
	List<Friend> friends2 = new ArrayList<>();

	@OneToMany(mappedBy = "fromUser")
	@Builder.Default
	List<FriendRequest> send = new ArrayList<>();

	@OneToMany(mappedBy = "toUser")
	@Builder.Default
	List<FriendRequest> recive = new ArrayList<>();

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	String id;

	@Column(name = "username", nullable = false, unique = true)
	String username;

	@Column(name = "password", nullable = false)
	String password;

	@Column(name = "displayName", nullable = false)
	String displayName;

	@Column(name = "birthday")
	Date birthday;

	@Column(name = "email", nullable = false, unique = true)
	String email;

	@Column(name = "phone", unique = true)
	String phone;

	@Column(name = "avatarUrl")
	String avatarUrl;

	@Column(name = "avatarId")
	String avatarId;

	@Column(name = "bio", length = 500)
	String bio;

	@Column(name = "createdAt")
	@CreatedDate
	Instant createdAt;

	@Column(name = "updatedAt")
	@LastModifiedDate
	Instant updatedAt;

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return Collections.emptyList();
	}

}
