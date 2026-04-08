package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.entity.Favorite;
import com.eaglepoint.storehub.entity.Post;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.FavoriteRepository;
import com.eaglepoint.storehub.repository.PostRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final SiteAuthorizationService siteAuth;

    @Audited(action = "TOGGLE_FAVORITE", entityType = "Favorite")
    @Transactional
    public boolean toggleFavorite(Long userId, Long postId) {
        Optional<Favorite> existing = favoriteRepository.findByUserIdAndPostId(userId, postId);

        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            log.info("Favorite removed: userId={}, postId={}", userId, postId);
            return false;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        // Site isolation: user must have access to the post's site
        if (post.getSite() != null) {
            siteAuth.requireSiteAccess(post.getSite().getId());
        }

        favoriteRepository.save(Favorite.builder().user(user).post(post).build());
        log.info("Favorite added: userId={}, postId={}", userId, postId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Long> getFavorites(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(f -> f.getPost().getId())
                .toList();
    }

    @Transactional(readOnly = true)
    public long getFavoriteCount(Long postId) {
        return favoriteRepository.countByPostId(postId);
    }
}
