package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.annotation.RequiresRecentAuth;
import com.eaglepoint.storehub.dto.*;
import com.eaglepoint.storehub.entity.QuarantinedVote;
import com.eaglepoint.storehub.enums.VoteType;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.CommunityService;
import com.eaglepoint.storehub.service.FavoriteService;
import com.eaglepoint.storehub.service.GamificationService;
import com.eaglepoint.storehub.service.UserFollowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final GamificationService gamificationService;
    private final FavoriteService favoriteService;
    private final UserFollowService userFollowService;
    private final com.eaglepoint.storehub.repository.UserRepository userRepository;
    private final com.eaglepoint.storehub.service.SiteAuthorizationService siteAuth;

    // ───── Posts ─────

    @PostMapping("/posts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PostRequest request) {
        return ResponseEntity.ok(communityService.createPost(principal.getId(), request));
    }

    @GetMapping("/posts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<PostResponse>> getFeed(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        return ResponseEntity.ok(communityService.getFeed(principal.getId(), pageable));
    }

    @GetMapping("/posts/topic/{topic}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<PostResponse>> getByTopic(
            @PathVariable String topic,
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        return ResponseEntity.ok(communityService.getFeedByTopic(topic, principal.getId(), pageable));
    }

    @GetMapping("/posts/following")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PostResponse>> getFollowedFeed(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(communityService.getFollowedFeed(principal.getId()));
    }

    @DeleteMapping("/posts/{id}")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<Void> removePost(@PathVariable Long id) {
        communityService.removePost(id);
        return ResponseEntity.noContent().build();
    }

    // ───── Comments ─────

    @PostMapping("/posts/{postId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.ok(communityService.addComment(postId, principal.getId(), request));
    }

    @GetMapping("/posts/{postId}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long postId) {
        return ResponseEntity.ok(communityService.getComments(postId));
    }

    // ───── Votes ─────

    @PostMapping("/posts/{postId}/vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostResponse> vote(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam VoteType type) {
        return ResponseEntity.ok(communityService.vote(postId, principal.getId(), type));
    }

    // ───── Topics ─────

    @PostMapping("/topics/{topic}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> followTopic(
            @PathVariable String topic,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.followTopic(principal.getId(), topic);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/topics/{topic}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unfollowTopic(
            @PathVariable String topic,
            @AuthenticationPrincipal UserPrincipal principal) {
        communityService.unfollowTopic(principal.getId(), topic);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/topics/following")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getFollowedTopics(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(communityService.getFollowedTopics(principal.getId()));
    }

    // ───── Points / Gamification ─────

    @GetMapping("/points/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PointsProfile> getMyPoints(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(gamificationService.getProfile(principal.getId()));
    }

    @GetMapping("/points/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PointsProfile> getUserPoints(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (!userId.equals(principal.getId())) {
            // Non-self: resolve target user's site and enforce scope
            var targetUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            Long targetSiteId = targetUser.getSite() != null ? targetUser.getSite().getId() : null;
            siteAuth.requireOwnerOrSiteAccess(userId, targetSiteId);
        }
        return ResponseEntity.ok(gamificationService.getProfile(userId));
    }

    // ───── Favorites ─────

    @PostMapping("/posts/{postId}/favorite")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> toggleFavorite(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean added = favoriteService.toggleFavorite(principal.getId(), postId);
        return ResponseEntity.ok(Map.of("postId", postId, "favorited", added));
    }

    @GetMapping("/favorites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Long>> getMyFavorites(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(favoriteService.getFavorites(principal.getId()));
    }

    // ───── User Following ─────

    @PostMapping("/users/{userId}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> followUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        userFollowService.follow(principal.getId(), userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{userId}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unfollowUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        userFollowService.unfollow(principal.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/following")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Long>> getFollowing(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userFollowService.getFollowing(principal.getId()));
    }

    // ───── Quarantine Review ─────

    @GetMapping("/quarantine/pending")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    public ResponseEntity<List<QuarantinedVote>> getPendingQuarantines() {
        return ResponseEntity.ok(communityService.getPendingQuarantines());
    }

    @PatchMapping("/quarantine/{id}/review")
    @PreAuthorize("hasAnyRole('ENTERPRISE_ADMIN','SITE_MANAGER')")
    @RequiresRecentAuth
    public ResponseEntity<Map<String, Object>> reviewQuarantine(
            @PathVariable Long id,
            @RequestParam boolean legitimate) {
        communityService.reviewQuarantine(id, legitimate);
        return ResponseEntity.ok(Map.of("id", id, "reviewed", true, "legitimate", legitimate));
    }
}
