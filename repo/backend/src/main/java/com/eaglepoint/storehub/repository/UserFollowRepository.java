package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    List<UserFollow> findByFollowerId(Long followerId);

    Optional<UserFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    long countByFollowingId(Long followingId);

    @Query("SELECT uf.following.id FROM UserFollow uf WHERE uf.follower.id = :followerId")
    List<Long> findFollowingIdsByFollowerId(@Param("followerId") Long followerId);
}
