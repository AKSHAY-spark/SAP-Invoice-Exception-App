package com.kaar.exceptions;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
/** Transparent, rules-based risk score (0-100). Every point is explained in riskReasons. */
@Service
public class RiskService {
  public void score(ExceptionCase c){
    int s=0; List<String> r=new ArrayList<>();
    double a=c.amount.doubleValue();
    if(a>=50000){s+=40;r.add("High value "+c.currency+" "+c.amount+" (+40)");}
    else if(a>=10000){s+=25;r.add("Medium value "+c.currency+" "+c.amount+" (+25)");}
    else {s+=10;r.add("Low value "+c.currency+" "+c.amount+" (+10)");}
    long d=ChronoUnit.DAYS.between(LocalDate.now(),c.dueDate);
    if(d<=3){s+=30;r.add("Due in "+d+" days or overdue (+30)");}
    else if(d<=10){s+=20;r.add("Due in "+d+" days (+20)");}
    else {s+=5;r.add("Due in "+d+" days (+5)");}
    long age=ChronoUnit.DAYS.between(c.createdDate,LocalDate.now());
    if(age>14){s+=20;r.add("Open "+age+" days (+20)");}
    else if(age>7){s+=10;r.add("Open "+age+" days (+10)");}
    if(c.type.equals("MISSING_MASTER_DATA")||c.type.equals("APPROVAL_PENDING")){s+=10;r.add("Compliance-sensitive type (+10)");}
    c.riskScore=s; c.riskReasons=r;
    c.riskLevel=s>=70?"CRITICAL":s>=50?"HIGH":s>=30?"MEDIUM":"LOW";
  }
}
