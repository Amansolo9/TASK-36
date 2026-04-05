package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.TopicFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicFollowRepository extends JpaRepository<TopicFollow, Long> {

    Optional<TopicFollow> findByUserIdAndTopic(Long userId, String topic);

    List<TopicFollow> findByUserId(Long userId);

    @Query("SELECT tf.topic FROM TopicFollow tf WHERE tf.user.id = :userId")
    List<String> findTopicsByUserId(@Param("userId") Long userId);

    long countByTopic(String topic);
}
