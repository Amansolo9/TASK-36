package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.entity.UserFollow;
import com.eaglepoint.storehub.repository.UserFollowRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFollowService {

    private final UserFollowRepository userFollowRepository;
    private final UserRepository userRepository;

    @Audited(action = "FOLLOW_USER", entityType = "UserFollow")
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }

        if (userFollowRepository.findByFollowerIdAndFollowingId(followerId, followingId).isPresent()) {
            throw new IllegalArgumentException("Already following this user");
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new IllegalArgumentException("Follower user not found"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new IllegalArgumentException("Following user not found"));

        userFollowRepository.save(UserFollow.builder().follower(follower).following(following).build());
        log.info("User follow created: followerId={}, followingId={}", followerId, followingId);
    }

    @Audited(action = "UNFOLLOW_USER", entityType = "UserFollow")
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        UserFollow follow = userFollowRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseThrow(() -> new IllegalArgumentException("Not following this user"));
        userFollowRepository.delete(follow);
        log.info("User follow removed: followerId={}, followingId={}", followerId, followingId);
    }

    @Transactional(readOnly = true)
    public List<Long> getFollowing(Long userId) {
        return userFollowRepository.findFollowingIdsByFollowerId(userId);
    }

    @Transactional(readOnly = true)
    public long getFollowerCount(Long userId) {
        return userFollowRepository.countByFollowingId(userId);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowRepository.findByFollowerIdAndFollowingId(followerId, followingId).isPresent();
    }
}
