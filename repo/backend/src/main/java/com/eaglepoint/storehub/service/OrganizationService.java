package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.annotation.DataScope;
import com.eaglepoint.storehub.aspect.DataScopeContext;
import com.eaglepoint.storehub.dto.OrganizationDto;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.enums.OrgLevel;
import com.eaglepoint.storehub.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Audited(action = "CREATE", entityType = "Organization")
    @Transactional
    public OrganizationDto create(OrganizationDto dto) {
        Organization parent = null;
        if (dto.getParentId() != null) {
            parent = organizationRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent org not found"));
        }

        Organization org = Organization.builder()
                .name(dto.getName())
                .level(dto.getLevel())
                .parent(parent)
                .build();

        org = organizationRepository.save(org);
        return toDto(org);
    }

    @DataScope
    @Transactional(readOnly = true)
    public List<OrganizationDto> findAll() {
        List<Long> visibleSiteIds = DataScopeContext.get();
        List<Organization> orgs;

        if (visibleSiteIds == null) {
            orgs = organizationRepository.findAll();
        } else {
            orgs = organizationRepository.findAllById(visibleSiteIds);
        }

        return orgs.stream().map(this::toDto).toList();
    }

    @DataScope
    @Transactional(readOnly = true)
    public List<OrganizationDto> findByLevel(OrgLevel level) {
        List<Long> visibleSiteIds = DataScopeContext.get();
        List<Organization> orgs = organizationRepository.findByLevel(level);
        if (visibleSiteIds != null) {
            orgs = orgs.stream().filter(o -> visibleSiteIds.contains(o.getId())
                    || (o.getParent() != null && visibleSiteIds.contains(o.getParent().getId()))).toList();
        }
        return orgs.stream().map(this::toDto).toList();
    }

    @DataScope
    @Transactional(readOnly = true)
    public List<OrganizationDto> findChildren(Long parentId) {
        List<Long> visibleSiteIds = DataScopeContext.get();
        List<Organization> children = organizationRepository.findByParentId(parentId);
        if (visibleSiteIds != null) {
            children = children.stream().filter(o -> visibleSiteIds.contains(o.getId())
                    || visibleSiteIds.contains(parentId)).toList();
        }
        return children.stream().map(this::toDto).toList();
    }

    private OrganizationDto toDto(Organization org) {
        OrganizationDto dto = new OrganizationDto();
        dto.setId(org.getId());
        dto.setName(org.getName());
        dto.setLevel(org.getLevel());
        dto.setParentId(org.getParent() != null ? org.getParent().getId() : null);
        return dto;
    }
}
