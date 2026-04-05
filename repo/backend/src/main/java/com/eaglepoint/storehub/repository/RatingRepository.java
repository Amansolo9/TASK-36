package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.Rating;
import com.eaglepoint.storehub.enums.AppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByRatedUserIdOrderByCreatedAtDesc(Long ratedUserId);

    Optional<Rating> findByOrderIdAndRaterId(Long orderId, Long raterId);

    @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.ratedUser.id = :userId")
    Double findAverageStarsByRatedUserId(@Param("userId") Long userId);

    List<Rating> findByAppealStatus(AppealStatus status);

    @Query("SELECT r FROM Rating r WHERE r.appealStatus = 'PENDING' AND r.appealDeadline < CURRENT_TIMESTAMP")
    List<Rating> findExpiredAppeals();
}
