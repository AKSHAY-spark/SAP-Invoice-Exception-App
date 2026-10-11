package com.kaar.exceptions;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
/** Seeds realistic mock SAP data on first start (stands in for S/4HANA OData services). */
@Component
public class DataSeeder implements CommandLineRunner {
  final CaseRepo repo; DataSeeder(CaseRepo r){repo=r;}
  public void run(String... a){
    if(repo.count()>0) return; LocalDate t=LocalDate.now();
    add("SRV-460034","Cobalt Engineering","1400","1107","Smart Meter SM-8","59250","APPROVAL_PENDING","AWAITING_APPROVAL",null,LocalDate.of(2026,10,19),LocalDate.of(2026,9,28),null,3,"Service entry above approval limit; approver has not acted");
    add("INV-770112","Delta Steel BV","1400","1102","Steel Coil SC-4","18400","PRICE_MISMATCH","OPEN","Ben",t.plusDays(2),t.minusDays(9),null,2,"Invoice price is 6% above PO price");
    add("INV-770145","Rhine Tyres GmbH","1400","1107","Tyre Set TS-2","7200","GR_MISSING","OPEN",null,t.plusDays(9),t.minusDays(16),null,1,"Goods receipt not posted by warehouse");
    add("INV-770201","Nordic Pallets AB","1500","1203","Pallet PL-9","2300","MISSING_MASTER_DATA","OPEN",null,t.plusDays(14),t.minusDays(3),null,0,"Supplier bank details missing in vendor master");
    add("INV-770230","Harbor Fuel Ltd","1400","1102","Diesel DF-1","41000","QTY_MISMATCH","AWAITING_APPROVAL","Chloe",t.plusDays(1),t.minusDays(12),null,2,"Invoiced quantity exceeds received quantity");
    add("INV-769900","Delta Steel BV","1400","1102","Steel Coil SC-4","9600","PRICE_MISMATCH","RESOLVED","Ben",t.minusDays(5),t.minusDays(12),t.minusDays(8),3,"Price corrected via credit note");
    add("INV-769950","Rhine Tyres GmbH","1400","1107","Tyre Set TS-2","5200","GR_MISSING","RESOLVED","Anna",t.minusDays(3),t.minusDays(11),t.minusDays(3),3,"Goods receipt posted late");
  }
  void add(String doc,String sup,String cc,String pl,String mat,String amt,String type,String st,String owner,
           LocalDate due,LocalDate cr,LocalDate res,int stuck,String root){
    var c=new ExceptionCase(); int n=(int)repo.count()+1;
    c.docNo=doc;c.supplier=sup;c.companyCode=cc;c.plant=pl;c.material=mat;c.amount=new BigDecimal(amt);c.currency="GBP";
    c.type=type;c.status=st;c.owner=owner;c.dueDate=due;c.createdDate=cr;c.resolvedDate=res;c.stuckStep=stuck;c.rootCause=root;
    c.poNumber="45000"+n; c.grNumber=stuck==1?null:"50000"+n; c.invoiceNumber=doc; c.fiDocument=res!=null?"19000"+n:null;
    repo.save(c);
  }
}
