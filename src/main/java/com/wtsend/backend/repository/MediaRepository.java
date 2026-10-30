package com.wtsend.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.wtsend.backend.model.Media;

public interface MediaRepository extends JpaRepository<Media, UUID> {

  Optional<Media> findByIdAndOwnerId(UUID id, String userId);

}
