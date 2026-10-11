package com.kaar.exceptions;
import jakarta.persistence.*;
import java.time.LocalDateTime;
/** Audit trail: @Immutable + no update/delete endpoint = non-editable. */
@Entity @org.hibernate.annotations.Immutable
public class AuditEntry {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public Long caseId; public String actor, role, action;
  @Column(length=1000) public String detail;
  public LocalDateTime ts = LocalDateTime.now();
  public AuditEntry(){}
  public AuditEntry(Long c,String a,String r,String ac,String d){caseId=c;actor=a;role=r;action=ac;detail=d;}
}
