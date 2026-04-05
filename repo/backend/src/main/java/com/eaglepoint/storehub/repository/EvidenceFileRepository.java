package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.EvidenceFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, Long> {

    List<EvidenceFile> findByTicketId(Long ticketId);
}
