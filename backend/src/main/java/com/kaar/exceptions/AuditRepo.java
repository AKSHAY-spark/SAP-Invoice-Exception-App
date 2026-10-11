package com.kaar.exceptions;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AuditRepo extends JpaRepository<AuditEntry,Long> { List<AuditEntry> findByCaseIdOrderByIdDesc(Long caseId); }
