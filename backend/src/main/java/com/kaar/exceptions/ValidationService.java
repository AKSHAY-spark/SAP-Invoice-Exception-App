package com.kaar.exceptions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {
  final CaseRepo repo;
  public ValidationService(CaseRepo r) { this.repo = r; }

  static final Set<String> VALID_TYPES = Set.of(
    "PRICE_MISMATCH", "GR_MISSING", "MISSING_MASTER_DATA", "APPROVAL_PENDING", "QTY_MISMATCH"
  );
  static final Set<String> VALID_STATUS = Set.of("OPEN", "AWAITING_APPROVAL", "RESOLVED");

  String s(Map<String, Object> b, String k) {
    if (b == null) return null;
    Object v = b.get(k);
    if (v == null) return null;
    String str = v.toString().trim();
    return str.isEmpty() ? null : str;
  }

  void err(List<Map<String, String>> list, String field, String message) {
    list.add(Map.of("field", field, "message", message));
  }

  public List<Map<String, String>> validate(Map<String, Object> b) {
    List<Map<String, String>> errors = new ArrayList<>();

    // Mandatory fields
    if (s(b, "docNo") == null) err(errors, "docNo", "Document number is required");
    if (s(b, "supplier") == null) err(errors, "supplier", "Supplier is required");
    if (s(b, "companyCode") == null) err(errors, "companyCode", "Company code is required");
    if (s(b, "plant") == null) err(errors, "plant", "Plant is required");
    if (s(b, "material") == null) err(errors, "material", "Material is required");
    if (s(b, "amount") == null) err(errors, "amount", "Amount is required");
    if (s(b, "type") == null) err(errors, "type", "Type is required");
    if (s(b, "status") == null) err(errors, "status", "Status is required");
    if (s(b, "dueDate") == null) err(errors, "dueDate", "Due date is required");
    if (s(b, "createdDate") == null) err(errors, "createdDate", "Created date is required");

    // Formats: companyCode and plant must be exactly 4 digits
    String cc = s(b, "companyCode");
    if (cc != null && !cc.matches("^\\d{4}$")) {
      err(errors, "companyCode", "Company code must be exactly 4 digits");
    }
    String pl = s(b, "plant");
    if (pl != null && !pl.matches("^\\d{4}$")) {
      err(errors, "plant", "Plant must be exactly 4 digits");
    }

    // Formats: amount is a number greater than 0
    String amtStr = s(b, "amount");
    if (amtStr != null) {
      try {
        BigDecimal amt = new BigDecimal(amtStr);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
          err(errors, "amount", "Amount must be greater than 0");
        }
      } catch (NumberFormatException e) {
        err(errors, "amount", "Amount must be a valid number greater than 0");
      }
    }

    // Formats: createdDate ISO yyyy-MM-dd and not in the future
    String crStr = s(b, "createdDate");
    LocalDate created = null;
    if (crStr != null) {
      try {
        created = LocalDate.parse(crStr);
        if (created.isAfter(LocalDate.now())) {
          err(errors, "createdDate", "Created date must not be in the future");
        }
      } catch (Exception e) {
        err(errors, "createdDate", "Created date must be in ISO format (yyyy-MM-dd)");
      }
    }

    // Formats: dueDate ISO yyyy-MM-dd and not before createdDate
    String dueStr = s(b, "dueDate");
    LocalDate due = null;
    if (dueStr != null) {
      try {
        due = LocalDate.parse(dueStr);
        if (created != null && due.isBefore(created)) {
          err(errors, "dueDate", "Due date must not be before created date");
        }
      } catch (Exception e) {
        err(errors, "dueDate", "Due date must be in ISO format (yyyy-MM-dd)");
      }
    }

    // Formats: type and status enum values
    String type = s(b, "type");
    if (type != null && !VALID_TYPES.contains(type)) {
      err(errors, "type", "Type must be one of: PRICE_MISMATCH, GR_MISSING, MISSING_MASTER_DATA, APPROVAL_PENDING, QTY_MISMATCH");
    }
    String status = s(b, "status");
    if (status != null && !VALID_STATUS.contains(status)) {
      err(errors, "status", "Status must be one of: OPEN, AWAITING_APPROVAL, RESOLVED");
    }

    // docNo must be unique
    String docNo = s(b, "docNo");
    if (docNo != null && repo.findAll().stream().anyMatch(c -> docNo.equalsIgnoreCase(c.docNo))) {
      err(errors, "docNo", "Document number must be unique (" + docNo + " already exists)");
    }

    // Document relationships: stuckStep 0 to 3
    String stepStr = s(b, "stuckStep");
    int step = 0;
    if (stepStr != null) {
      try {
        step = Integer.parseInt(stepStr);
        if (step < 0 || step > 3) {
          err(errors, "stuckStep", "Stuck step must be between 0 and 3");
        }
      } catch (NumberFormatException e) {
        err(errors, "stuckStep", "Stuck step must be a number between 0 and 3");
      }
    }

    String po = s(b, "poNumber");
    String gr = s(b, "grNumber");
    String fi = s(b, "fiDocument");
    String resStr = s(b, "resolvedDate");

    // If stuckStep is 1, grNumber must be empty
    if (step == 1 && gr != null) {
      err(errors, "grNumber", "Goods receipt number must be empty when stuck step is 1");
    }

    // If stuckStep is 2 or 3, poNumber and grNumber are required
    if (step == 2 || step == 3) {
      if (po == null) {
        err(errors, "poNumber", "Purchase order number is required when stuck step is " + step);
      }
      if (gr == null) {
        err(errors, "grNumber", "Goods receipt number is required when stuck step is " + step);
      }
    }

    // If stuckStep is 3 and status is not RESOLVED, fiDocument must be empty
    if (step == 3 && !"RESOLVED".equals(status) && fi != null) {
      err(errors, "fiDocument", "FI document must be empty when stuck step is 3 and status is not RESOLVED");
    }

    // If status is RESOLVED, resolvedDate and fiDocument are required and resolvedDate must not be before createdDate
    if ("RESOLVED".equals(status)) {
      if (fi == null) {
        err(errors, "fiDocument", "FI document is required when status is RESOLVED");
      }
      if (resStr == null) {
        err(errors, "resolvedDate", "Resolved date is required when status is RESOLVED");
      } else {
        try {
          LocalDate res = LocalDate.parse(resStr);
          if (created != null && res.isBefore(created)) {
            err(errors, "resolvedDate", "Resolved date must not be before created date");
          }
        } catch (Exception e) {
          err(errors, "resolvedDate", "Resolved date must be in ISO format (yyyy-MM-dd)");
        }
      }
    } else {
      // If status is not RESOLVED, resolvedDate must be empty
      if (resStr != null) {
        err(errors, "resolvedDate", "Resolved date must be empty when status is not RESOLVED");
      }
    }

    return errors;
  }

  public ExceptionCase buildCase(Map<String, Object> b) {
    ExceptionCase c = new ExceptionCase();
    c.docNo = s(b, "docNo");
    c.supplier = s(b, "supplier");
    c.companyCode = s(b, "companyCode");
    c.plant = s(b, "plant");
    c.material = s(b, "material");
    c.amount = new BigDecimal(s(b, "amount"));
    c.currency = s(b, "currency") != null ? s(b, "currency") : "GBP";
    c.type = s(b, "type");
    c.status = s(b, "status");
    c.owner = s(b, "owner");
    c.dueDate = LocalDate.parse(s(b, "dueDate"));
    c.createdDate = LocalDate.parse(s(b, "createdDate"));
    String stepStr = s(b, "stuckStep");
    c.stuckStep = stepStr != null ? Integer.parseInt(stepStr) : 0;
    c.rootCause = s(b, "rootCause");
    c.poNumber = s(b, "poNumber");
    c.grNumber = s(b, "grNumber");
    c.invoiceNumber = s(b, "invoiceNumber") != null ? s(b, "invoiceNumber") : c.docNo;
    c.fiDocument = s(b, "fiDocument");
    if (s(b, "resolvedDate") != null) {
      c.resolvedDate = LocalDate.parse(s(b, "resolvedDate"));
    }
    return c;
  }
}
