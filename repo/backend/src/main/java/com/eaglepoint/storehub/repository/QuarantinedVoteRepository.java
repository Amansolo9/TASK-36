package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.QuarantinedVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuarantinedVoteRepository extends JpaRepository<QuarantinedVote, Long> {

    List<QuarantinedVote> findByReviewedFalseOrderByDetectedAtDesc();

    boolean existsByVoterIdAndPostAuthorIdAndReviewedFalse(Long voterId, Long postAuthorId);
}
