package com.kaar.exceptions;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CaseRepo extends JpaRepository<ExceptionCase,Long> {}
