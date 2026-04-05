package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.enums.OrgLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    List<Organization> findByLevel(OrgLevel level);

    List<Organization> findByParentId(Long parentId);

    @Query("SELECT o FROM Organization o WHERE o.parent.id = :parentId AND o.level = :level")
    List<Organization> findByParentIdAndLevel(@Param("parentId") Long parentId, @Param("level") OrgLevel level);

    @Query(value = """
        WITH RECURSIVE org_tree AS (
            SELECT id, name, level, parent_id FROM organizations WHERE id = :rootId
            UNION ALL
            SELECT o.id, o.name, o.level, o.parent_id
            FROM organizations o JOIN org_tree t ON o.parent_id = t.id
        )
        SELECT id FROM org_tree WHERE level = 'SITE'
        """, nativeQuery = true)
    List<Long> findAllSiteIdsUnder(@Param("rootId") Long rootId);
}
