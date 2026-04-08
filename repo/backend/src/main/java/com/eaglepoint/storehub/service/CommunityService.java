package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.annotation.DataScope;
import com.eaglepoint.storehub.aspect.DataScopeContext;
import com.eaglepoint.storehub.dto.*;
import com.eaglepoint.storehub.entity.*;
import com.eaglepoint.storehub.enums.PointAction;
import com.eaglepoint.storehub.enums.VoteType;
import com.eaglepoint.storehub.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final VoteRepository voteRepository;
    private final TopicFollowRepository topicFollowRepository;
    private final UserFollowRepository userFollowRepository;
    private final QuarantinedVoteRepository quarantinedVoteRepository;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;
    private final SiteAuthorizationService siteAuth;

    // ───── Posts ─────

    @Audited(action = "CREATE", entityType = "Post")
    @Transactional
    public PostResponse createPost(Long authorId, PostRequest request) {
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Post post = Post.builder()
                .author(author)
                .site(author.getSite()) // Site linkage from author
                .title(request.getTitle())
                .body(request.getBody())
                .topic(request.getTopic())
                .build();

        post = postRepository.save(post);
        log.info("Post created: postId={}, authorId={}, topic='{}'", post.getId(), authorId, post.getTopic());
        gamificationService.awardPoints(authorId, PointAction.POST_CREATED,
                "Created post: " + post.getTitle());

        return toPostResponse(post, authorId);
    }

    @DataScope
    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(Long currentUserId, Pageable pageable) {
        List<Long> visibleSites = DataScopeContext.get();
        if (visibleSites != null) {
            return postRepository.findBySiteIdInAndRemovedFalseOrderByCreatedAtDesc(visibleSites, pageable)
                    .map(p -> toPostResponse(p, currentUserId));
        }
        return postRepository.findByRemovedFalseOrderByCreatedAtDesc(pageable)
                .map(p -> toPostResponse(p, currentUserId));
    }

    @DataScope
    @Transactional(readOnly = true)
    public Page<PostResponse> getFeedByTopic(String topic, Long currentUserId, Pageable pageable) {
        List<Long> visibleSites = DataScopeContext.get();
        if (visibleSites != null && !visibleSites.isEmpty()) {
            return postRepository.findBySiteIdInAndTopicAndRemovedFalseOrderByCreatedAtDesc(
                    visibleSites, topic, pageable)
                    .map(p -> toPostResponse(p, currentUserId));
        }
        return postRepository.findByTopicAndRemovedFalseOrderByCreatedAtDesc(topic, pageable)
                .map(p -> toPostResponse(p, currentUserId));
    }

    @DataScope
    @Transactional(readOnly = true)
    public List<PostResponse> getFollowedFeed(Long userId) {
        List<Long> followedAuthorIds = userFollowRepository.findFollowingIdsByFollowerId(userId);
        if (followedAuthorIds.isEmpty()) return List.of();
        List<Long> visibleSites = DataScopeContext.get();
        if (visibleSites != null) {
            return postRepository.findByAuthorIdInAndSiteIdInAndRemovedFalseOrderByCreatedAtDesc(
                    followedAuthorIds, visibleSites).stream()
                .map(p -> toPostResponse(p, userId)).toList();
        }
        return postRepository.findByAuthorIdInAndRemovedFalseOrderByCreatedAtDesc(followedAuthorIds).stream()
                .map(p -> toPostResponse(p, userId)).toList();
    }

    @Audited(action = "REMOVE", entityType = "Post")
    @Transactional
    public void removePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        requirePostSiteAccess(post);
        post.setRemoved(true);
        log.info("Post removed: postId={}, authorId={}", postId, post.getAuthor().getId());
        postRepository.save(post);
        gamificationService.awardPoints(post.getAuthor().getId(), PointAction.POST_REMOVED,
                "Post removed: " + post.getTitle());
    }

    // ───── Comments ─────

    @Audited(action = "CREATE", entityType = "Comment")
    @Transactional
    public CommentResponse addComment(Long postId, Long authorId, CommentRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        requirePostSiteAccess(post);
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Comment comment = Comment.builder()
                .post(post)
                .author(author)
                .body(request.getBody())
                .build();

        comment = commentRepository.save(comment);
        gamificationService.awardPoints(authorId, PointAction.COMMENT_CREATED,
                "Commented on: " + post.getTitle());

        return toCommentResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        requirePostSiteAccess(post);
        return commentRepository.findByPostIdAndRemovedFalseOrderByCreatedAtAsc(postId).stream()
                .map(this::toCommentResponse).toList();
    }

    // ───── Votes ─────

    @Audited(action = "VOTE", entityType = "Post")
    @Transactional
    public PostResponse vote(Long postId, Long userId, VoteType type) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        requirePostSiteAccess(post);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (post.getAuthor().getId().equals(userId)) {
            throw new IllegalArgumentException("Cannot vote on your own post");
        }

        Optional<Vote> existing = voteRepository.findByUserIdAndPostId(userId, postId);

        if (existing.isPresent()) {
            Vote vote = existing.get();
            if (vote.getType() == type) {
                // Remove vote (toggle off)
                voteRepository.delete(vote);
                if (type == VoteType.UPVOTE) {
                    post.setUpvotes(post.getUpvotes() - 1);
                } else {
                    post.setDownvotes(post.getDownvotes() - 1);
                }
            } else {
                // Switch vote
                VoteType oldType = vote.getType();
                vote.setType(type);
                voteRepository.save(vote);
                if (oldType == VoteType.UPVOTE) {
                    post.setUpvotes(post.getUpvotes() - 1);
                    post.setDownvotes(post.getDownvotes() + 1);
                } else {
                    post.setDownvotes(post.getDownvotes() - 1);
                    post.setUpvotes(post.getUpvotes() + 1);
                }
            }
        } else {
            // New vote
            Vote vote = Vote.builder().user(user).post(post).type(type).build();
            voteRepository.save(vote);
            if (type == VoteType.UPVOTE) {
                post.setUpvotes(post.getUpvotes() + 1);
                gamificationService.awardPoints(post.getAuthor().getId(),
                        PointAction.UPVOTE_RECEIVED, "Upvote on: " + post.getTitle());
            } else {
                post.setDownvotes(post.getDownvotes() + 1);
                gamificationService.awardPoints(post.getAuthor().getId(),
                        PointAction.DOWNVOTE_RECEIVED, "Downvote on: " + post.getTitle());
            }
        }

        post = postRepository.save(post);
        log.info("Vote recorded: postId={}, userId={}, voteType={}", postId, userId, type);
        return toPostResponse(post, userId);
    }

    // ───── Topics ─────

    @Audited(action = "FOLLOW_TOPIC", entityType = "TopicFollow")
    @Transactional
    public void followTopic(Long userId, String topic) {
        if (topicFollowRepository.findByUserIdAndTopic(userId, topic).isPresent()) {
            throw new IllegalArgumentException("Already following topic: " + topic);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        topicFollowRepository.save(TopicFollow.builder().user(user).topic(topic).build());
    }

    @Audited(action = "UNFOLLOW_TOPIC", entityType = "TopicFollow")
    @Transactional
    public void unfollowTopic(Long userId, String topic) {
        TopicFollow follow = topicFollowRepository.findByUserIdAndTopic(userId, topic)
                .orElseThrow(() -> new IllegalArgumentException("Not following topic: " + topic));
        topicFollowRepository.delete(follow);
    }

    @Transactional(readOnly = true)
    public List<String> getFollowedTopics(Long userId) {
        return topicFollowRepository.findTopicsByUserId(userId);
    }

    // ───── Quarantine Review ─────

    @Transactional(readOnly = true)
    public List<QuarantinedVote> getPendingQuarantines() {
        return quarantinedVoteRepository.findByReviewedFalseOrderByDetectedAtDesc().stream()
                .filter(qv -> {
                    // Scope by post author's site
                    Long authorSiteId = (qv.getPostAuthor() != null && qv.getPostAuthor().getSite() != null)
                            ? qv.getPostAuthor().getSite().getId() : null;
                    return siteAuth.canAccessSite(authorSiteId);
                }).toList();
    }

    @Audited(action = "REVIEW_QUARANTINE", entityType = "QuarantinedVote")
    @Transactional
    public QuarantinedVote reviewQuarantine(Long quarantineId, boolean legitimate) {
        QuarantinedVote qv = quarantinedVoteRepository.findById(quarantineId)
                .orElseThrow(() -> new IllegalArgumentException("Quarantined vote not found"));

        // Site-scope enforcement — derive from post author's site
        Long authorSiteId = (qv.getPostAuthor() != null && qv.getPostAuthor().getSite() != null)
                ? qv.getPostAuthor().getSite().getId() : null;
        siteAuth.requireSiteAccess(authorSiteId);

        qv.setReviewed(true);
        quarantinedVoteRepository.save(qv);

        if (!legitimate) {
            // False positive: reverse the -20 QUARANTINED penalty with +20 QUARANTINE_REVERSED
            gamificationService.awardPoints(qv.getVoter().getId(), PointAction.QUARANTINE_REVERSED,
                    "Quarantine reversed: false positive (penalty nullified)");
            gamificationService.awardPoints(qv.getPostAuthor().getId(), PointAction.QUARANTINE_REVERSED,
                    "Quarantine reversed: false positive (penalty nullified)");
        }
        log.info("Quarantine reviewed: id={}, legitimate={}", quarantineId, legitimate);
        return qv;
    }

    // ───── Post-site access helper ─────

    private void requirePostSiteAccess(Post post) {
        Long postSiteId = post.getSite() != null ? post.getSite().getId() : null;
        // Deny-by-default: null-site posts are admin-only (siteAuth denies null for non-admins)
        siteAuth.requireSiteAccess(postSiteId);
    }

    // ───── Mappers ─────

    private PostResponse toPostResponse(Post post, Long currentUserId) {
        PostResponse r = new PostResponse();
        r.setId(post.getId());
        r.setAuthorId(post.getAuthor().getId());
        r.setAuthorName(post.getAuthor().getUsername());
        r.setTitle(post.getTitle());
        r.setBody(post.getBody());
        r.setTopic(post.getTopic());
        r.setUpvotes(post.getUpvotes());
        r.setDownvotes(post.getDownvotes());
        r.setNetVotes(post.getUpvotes() - post.getDownvotes());
        r.setCommentCount(commentRepository.countByPostIdAndRemovedFalse(post.getId()));
        r.setCreatedAt(post.getCreatedAt());

        if (currentUserId != null) {
            voteRepository.findByUserIdAndPostId(currentUserId, post.getId())
                    .ifPresent(v -> r.setCurrentUserVote(v.getType().name()));
        }

        return r;
    }

    private CommentResponse toCommentResponse(Comment c) {
        CommentResponse r = new CommentResponse();
        r.setId(c.getId());
        r.setPostId(c.getPost().getId());
        r.setAuthorId(c.getAuthor().getId());
        r.setAuthorName(c.getAuthor().getUsername());
        r.setBody(c.getBody());
        r.setCreatedAt(c.getCreatedAt());
        return r;
    }
}
