package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserId(Long userId);

    Optional<Favorite> findByUserIdAndPostId(Long userId, Long postId);

    long countByPostId(Long postId);
}
