package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByUserIdAndPostId(Long userId, Long postId);

    @Query(value = """
        SELECT v.user_id AS voter_id, p.author_id AS author_id, COUNT(*) AS vote_count
        FROM votes v
        JOIN posts p ON v.post_id = p.id
        WHERE v.created_at > :since
        GROUP BY v.user_id, p.author_id
        HAVING COUNT(*) > :threshold
        """, nativeQuery = true)
    List<Object[]> findLikeRings(@Param("since") Instant since, @Param("threshold") int threshold);
}
