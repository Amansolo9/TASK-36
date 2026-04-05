package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdAndRemovedFalseOrderByCreatedAtAsc(Long postId);

    int countByPostIdAndRemovedFalse(Long postId);
}
