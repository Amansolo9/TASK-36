package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.site.id = :siteId")
    List<User> findAllBySiteId(@Param("siteId") Long siteId);

    @Query("SELECT u FROM User u WHERE u.site.id IN :siteIds")
    List<User> findAllBySiteIdIn(@Param("siteIds") List<Long> siteIds);
}
