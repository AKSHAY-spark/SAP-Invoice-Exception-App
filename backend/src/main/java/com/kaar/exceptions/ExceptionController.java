package com.kaar.exceptions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api") @CrossOrigin
public class ExceptionController {
  final CaseRepo repo; final AuditRepo audit; final RiskService risk;
  public ExceptionController(CaseRepo r,AuditRepo a,RiskService k){repo=r;audit=a;risk=k;}

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
