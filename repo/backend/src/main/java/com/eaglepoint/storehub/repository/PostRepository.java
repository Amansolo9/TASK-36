package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByRemovedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<Post> findByTopicAndRemovedFalseOrderByCreatedAtDesc(String topic, Pageable pageable);

    List<Post> findByAuthorIdAndRemovedFalseOrderByCreatedAtDesc(Long authorId);

    List<Post> findByTopicInAndRemovedFalseOrderByCreatedAtDesc(List<String> topics);

    List<Post> findByAuthorIdInAndRemovedFalseOrderByCreatedAtDesc(List<Long> authorIds);

    // Site-scoped queries
    Page<Post> findBySiteIdAndRemovedFalseOrderByCreatedAtDesc(Long siteId, Pageable pageable);

    Page<Post> findBySiteIdAndTopicAndRemovedFalseOrderByCreatedAtDesc(Long siteId, String topic, Pageable pageable);

    Page<Post> findBySiteIdInAndRemovedFalseOrderByCreatedAtDesc(List<Long> siteIds, Pageable pageable);

    List<Post> findByAuthorIdInAndSiteIdInAndRemovedFalseOrderByCreatedAtDesc(List<Long> authorIds, List<Long> siteIds);
}
