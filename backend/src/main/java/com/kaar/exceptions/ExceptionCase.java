package com.kaar.exceptions;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
/** One stuck supplier invoice, with the linked SAP document numbers (mock S/4HANA data). */
@Entity
public class ExceptionCase {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public String docNo, supplier, companyCode, plant, material, currency, status, type, owner;
  public String poNumber, grNumber, invoiceNumber, fiDocument, rootCause;
  public BigDecimal amount;
  public LocalDate dueDate, createdDate, resolvedDate;
  public int stuckStep; // 0=PO 1=Goods Receipt 2=Invoice 3=FI posting
  @Transient public int riskScore; @Transient public String riskLevel; @Transient public List<String> riskReasons;
}
