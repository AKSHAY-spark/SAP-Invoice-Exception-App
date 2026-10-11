package com.kaar.exceptions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api") @CrossOrigin
public class ExceptionController {
  final CaseRepo repo; final AuditRepo audit; final RiskService risk; final ValidationService validator;
  @Value("${app.mock.invoices-processed:120}") int invoicesProcessed;
  public ExceptionController(CaseRepo r,AuditRepo a,RiskService k,ValidationService v){repo=r;audit=a;risk=k;validator=v;}

  List<ExceptionCase> all(){ var l=repo.findAll(); l.forEach(risk::score); return l; }
  boolean m(Map<String,String> p,String k,String v){ String f=p.get(k); return f==null||f.isEmpty()||f.equals(v); }

  @GetMapping("/exceptions") // searchable, filterable, sorted by risk score
  public List<ExceptionCase> list(@RequestParam Map<String,String> p){
    String q=p.getOrDefault("q","").toLowerCase();
    return all().stream().filter(c->(q.isEmpty()||(c.docNo+c.supplier+c.material).toLowerCase().contains(q))
      &&m(p,"companyCode",c.companyCode)&&m(p,"plant",c.plant)&&m(p,"supplier",c.supplier)
      &&m(p,"status",c.status)&&m(p,"risk",c.riskLevel)&&m(p,"owner",c.owner))
      .sorted((x,y)->y.riskScore-x.riskScore).toList();
  }

  @PostMapping("/exceptions")
  public ResponseEntity<?> create(@RequestBody(required=false) Map<String,Object> b,
      @RequestHeader(value="X-User",defaultValue="System") String user,
      @RequestHeader(value="X-Role",defaultValue="MANAGER") String role){
    if(!role.equals("MANAGER")&&!role.equals("AP_CLERK"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only a manager or AP clerk can create");
    var errs=validator.validate(b);
    if(!errs.isEmpty()) return ResponseEntity.badRequest().body(Map.of("errors",errs));
    var c=validator.buildCase(b);
    repo.save(c);
    audit.save(new AuditEntry(c.id,user,role,"create","Exception created"));
    risk.score(c);
    return ResponseEntity.ok(c);
  }

  @GetMapping("/exceptions/export")
  public ResponseEntity<byte[]> export(@RequestParam Map<String,String> p) throws Exception {
    List<ExceptionCase> rows = list(p);
    try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
         var out = new java.io.ByteArrayOutputStream()) {
      var sheet = wb.createSheet("Exceptions");
      var headerStyle = wb.createCellStyle();
      var headerFont = wb.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);
      var amtStyle = wb.createCellStyle();
      amtStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
      var totalLabelStyle = wb.createCellStyle();
      totalLabelStyle.setFont(headerFont);
      var totalAmtStyle = wb.createCellStyle();
      totalAmtStyle.cloneStyleFrom(amtStyle);
      totalAmtStyle.setFont(headerFont);

      String[] headers = {"Document","Supplier","Company Code","Plant","Material","Amount","Currency","Type","Status","Owner","Due Date","Created Date","Risk Score","Risk Level","Root Cause"};
      var headerRow = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        var cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      int rowIdx = 1;
      BigDecimal total = BigDecimal.ZERO;
      for (var c : rows) {
        var row = sheet.createRow(rowIdx++);
        row.createCell(0).setCellValue(c.docNo != null ? c.docNo : "");
        row.createCell(1).setCellValue(c.supplier != null ? c.supplier : "");
        row.createCell(2).setCellValue(c.companyCode != null ? c.companyCode : "");
        row.createCell(3).setCellValue(c.plant != null ? c.plant : "");
        row.createCell(4).setCellValue(c.material != null ? c.material : "");
        var amtCell = row.createCell(5);
        if (c.amount != null) {
          amtCell.setCellValue(c.amount.doubleValue());
          total = total.add(c.amount);
        } else {
          amtCell.setCellValue(0.0);
        }
        amtCell.setCellStyle(amtStyle);
        row.createCell(6).setCellValue(c.currency != null ? c.currency : "GBP");
        row.createCell(7).setCellValue(c.type != null ? c.type : "");
        row.createCell(8).setCellValue(c.status != null ? c.status : "");
        row.createCell(9).setCellValue(c.owner != null ? c.owner : "Unassigned");
        row.createCell(10).setCellValue(c.dueDate != null ? c.dueDate.toString() : "");
        row.createCell(11).setCellValue(c.createdDate != null ? c.createdDate.toString() : "");
        row.createCell(12).setCellValue(c.riskScore);
        row.createCell(13).setCellValue(c.riskLevel != null ? c.riskLevel : "");
        row.createCell(14).setCellValue(c.rootCause != null ? c.rootCause : "");
      }

      var totalRow = sheet.createRow(rowIdx);
      var totalLabel = totalRow.createCell(0);
      totalLabel.setCellValue("Total");
      totalLabel.setCellStyle(totalLabelStyle);
      var totalAmtCell = totalRow.createCell(5);
      totalAmtCell.setCellValue(total.doubleValue());
      totalAmtCell.setCellStyle(totalAmtStyle);

      int[] widths = {15, 25, 15, 12, 20, 15, 12, 22, 20, 15, 14, 14, 12, 14, 40};
      for (int i = 0; i < widths.length; i++) {
        sheet.setColumnWidth(i, widths[i] * 256);
      }

      wb.write(out);
      return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"exceptions.xlsx\"")
        .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .body(out.toByteArray());
    }
  }

  @GetMapping("/exceptions/{id}")
  public ExceptionCase one(@PathVariable Long id){ var c=repo.findById(id).orElseThrow(); risk.score(c); return c; }
  @GetMapping("/exceptions/{id}/audit")
  public List<AuditEntry> trail(@PathVariable Long id){ return audit.findByCaseIdOrderByIdDesc(id); }

  @PostMapping("/exceptions/{id}/{action}") // assign | comment | request-approval | resolve
  public ExceptionCase act(@PathVariable Long id,@PathVariable String action,@RequestBody(required=false) Map<String,String> b,
      @RequestHeader("X-User") String user,@RequestHeader("X-Role") String role){
    var c=repo.findById(id).orElseThrow(); if(b==null) b=Map.of(); String detail;
    switch(action){
      case "assign": c.owner=b.get("owner"); detail="Assigned to "+c.owner; break;
      case "comment": detail=b.get("text"); break;
      case "request-approval": c.status="AWAITING_APPROVAL"; detail="Approval requested"; break;
      case "resolve":
        if(!role.equals("MANAGER")&&!role.equals("APPROVER"))
          throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only a manager or approver can resolve");
        c.status="RESOLVED"; c.resolvedDate=LocalDate.now(); detail="Resolved. "+b.getOrDefault("text",""); break;
      default: throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    repo.save(c); audit.save(new AuditEntry(id,user,role,action,detail)); risk.score(c); return c;
  }

  @GetMapping("/dashboard")
  public Map<String,Object> dash(){
    var l=all(); var open=l.stream().filter(c->!c.status.equals("RESOLVED")).toList();
    var res=l.stream().filter(c->c.resolvedDate!=null).toList();
    double avg=res.stream().mapToLong(c->ChronoUnit.DAYS.between(c.createdDate,c.resolvedDate)).average().orElse(0);
    long ok=res.stream().filter(c->ChronoUnit.DAYS.between(c.createdDate,c.resolvedDate)<=5).count(); // SLA = 5 days
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("open",open.size());
    m.put("critical",open.stream().filter(c->c.riskLevel.equals("CRITICAL")).count());
    m.put("exposure",open.stream().map(c->c.amount).reduce(BigDecimal.ZERO,BigDecimal::add));
    m.put("avgResolutionDays",Math.round(avg*10)/10.0);
    m.put("slaPercent",res.isEmpty()?100:Math.round(100.0*ok/res.size()));

    LocalDate today = LocalDate.now();
    BigDecimal overdue = open.stream().filter(c -> c.dueDate != null && c.dueDate.isBefore(today))
      .map(c -> c.amount != null ? c.amount : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
    m.put("overdueValue", overdue);

    long totalExceptions = l.size();
    long fpy = 0;
    if (invoicesProcessed > 0) {
      long noEx = invoicesProcessed - totalExceptions;
      if (noEx > 0) fpy = Math.round(100.0 * noEx / invoicesProcessed);
    }
    m.put("firstPassYield", fpy);
    return m;
  }

  @GetMapping("/analytics")
  public Map<String,Object> analytics(){
    var o=all().stream().filter(c->!c.status.equals("RESOLVED")).toList();
    Map<String,Object> m=new LinkedHashMap<>();
    m.put("Open by risk",cnt(o,c->c.riskLevel)); m.put("Open by exception type",cnt(o,c->c.type));
    m.put("Open by plant",cnt(o,c->c.plant));
    m.put("Ageing",cnt(o,c->{long a=ChronoUnit.DAYS.between(c.createdDate,LocalDate.now());return a<=7?"0-7 days":a<=14?"8-14 days":"15+ days";}));
    m.put("Financial exposure by plant",o.stream().collect(Collectors.groupingBy(c->c.plant,TreeMap::new,Collectors.reducing(BigDecimal.ZERO,c->c.amount,BigDecimal::add))));
    return m;
  }
  Map<String,Long> cnt(List<ExceptionCase> l,Function<ExceptionCase,String> f){
    return l.stream().collect(Collectors.groupingBy(f,TreeMap::new,Collectors.counting()));
  }
}
