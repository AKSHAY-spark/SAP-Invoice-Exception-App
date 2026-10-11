import {useEffect,useState} from 'react';
const USERS=[{name:'Anna',role:'MANAGER'},{name:'Ben',role:'AP_CLERK'},{name:'Chloe',role:'APPROVER'}];
const money=n=>'£'+Number(n).toLocaleString('en-GB');
const Tag=({v})=><span className={'tag '+v}>{v}</span>;
const NEXT={APPROVAL_PENDING:'Chase the approver and request approval; resolve once approved.',PRICE_MISMATCH:'Ask the supplier for a credit note or correct the PO price.',
  GR_MISSING:'Ask the warehouse to post the goods receipt.',MISSING_MASTER_DATA:'Ask procurement to complete the vendor master record.',QTY_MISMATCH:'Verify the delivered quantity, then request approval for a partial payment.'};

function Bars({title,data}){const max=Math.max(1,...Object.values(data));
  return <div className="card"><h4>{title}</h4>{Object.entries(data).map(([k,v])=>
    <div className="bar" key={k}><span>{k}</span><i style={{width:(v/max*100)+'%'}}/><b>{v>999?money(v):v}</b></div>)}</div>}

function Analytics({api}){const [d,setD]=useState(null);useEffect(()=>{api('/analytics').then(setD)},[]);
  if(!d)return <p>Loading…</p>;
  return <div className="grid">{Object.entries(d).map(([t,v])=><Bars key={t} title={t} data={v}/>)}</div>}

function Queue({api,open,user}){
  const [k,setK]=useState({}),[rows,setRows]=useState([]),[all,setAll]=useState([]),[f,setF]=useState({});
  const [showForm,setShowForm]=useState(false);
  const [exportErr,setExportErr]=useState('');
  const [formErrors,setFormErrors]=useState([]);
  const [summaryErr,setSummaryErr]=useState('');
  const todayStr=new Date().toISOString().split('T')[0];
  const initialForm={
    docNo:'',supplier:'',companyCode:'',plant:'',material:'',amount:'',currency:'GBP',
    type:'PRICE_MISMATCH',status:'OPEN',stuckStep:0,createdDate:todayStr,dueDate:'',
    poNumber:'',grNumber:'',invoiceNumber:'',fiDocument:'',resolvedDate:'',rootCause:'',owner:''
  };
  const [formData,setFormData]=useState(initialForm);

  const refresh=()=>{
    api('/dashboard').then(setK);
    api('/exceptions').then(setAll);
    api('/exceptions?'+new URLSearchParams(Object.entries(f).filter(([,v])=>v))).then(setRows);
  };

  useEffect(()=>{api('/dashboard').then(setK);api('/exceptions').then(setAll)},[]);
  useEffect(()=>{api('/exceptions?'+new URLSearchParams(Object.entries(f).filter(([,v])=>v))).then(setRows)},[f]);

  const FILTERS=[['companyCode','companyCode'],['plant','plant'],['supplier','supplier'],['status','status'],['risk','riskLevel'],['owner','owner']];

  const exportExcel=()=>{
    setExportErr('');
    const q=new URLSearchParams(Object.entries(f).filter(([,v])=>v)).toString();
    fetch('/api/exceptions/export?'+q,{
      headers:{'X-User':user.name,'X-Role':user.role}
    })
    .then(res=>{
      if(!res.ok) throw new Error('Export failed ('+res.statusText+')');
      return res.blob();
    })
    .then(blob=>{
      const url=window.URL.createObjectURL(blob);
      const a=document.createElement('a');
      a.href=url;
      a.download='exceptions.xlsx';
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    })
    .catch(e=>setExportErr(e.message||'Export failed'));
  };

  const submitForm=e=>{
    e.preventDefault();
    setFormErrors([]);
    setSummaryErr('');
    fetch('/api/exceptions',{
      method:'POST',
      headers:{'Content-Type':'application/json','X-User':user.name,'X-Role':user.role},
      body:JSON.stringify(formData)
    })
    .then(async res=>{
      const data=await res.json().catch(()=>({}));
      if(!res.ok){
        if(data.errors){
          setFormErrors(data.errors);
          setSummaryErr('Please correct the '+data.errors.length+' error(s) below.');
        } else {
          setSummaryErr(data.message||'Creation failed ('+res.status+')');
        }
        return;
      }
      setShowForm(false);
      setFormData(initialForm);
      setFormErrors([]);
      setSummaryErr('');
      refresh();
    })
    .catch(err=>setSummaryErr(err.message||'Request failed'));
  };

  const errMap=Object.fromEntries(formErrors.map(e=>[e.field,e.message]));

  return <>
    <div className="kpis">
      <div className="card"><b>{k.open}</b><span>Open exceptions</span></div>
      <div className="card"><b>{k.critical}</b><span>Critical cases</span></div>
      <div className="card"><b>{k.exposure!=null&&money(k.exposure)}</b><span>Financial exposure</span></div>
      <div className="card"><b>{k.overdueValue!=null&&money(k.overdueValue)}</b><span>Overdue value</span></div>
      <div className="card"><b>{k.firstPassYield!=null?k.firstPassYield+'%':'—'}</b><span>First-pass yield</span></div>
      <div className="card"><b>{k.avgResolutionDays} d</b><span>Avg resolution time</span></div>
      <div className="card"><b>{k.slaPercent}%</b><span>SLA adherence (5 days)</span></div></div>

    <div style={{marginBottom:10}}>
      <button className="btn" onClick={()=>{setShowForm(!showForm);setFormErrors([]);setSummaryErr('')}}>
        {showForm?'Cancel new exception':'New exception'}
      </button>
    </div>

    {showForm&&<div className="card">
      <h4>Create New Invoice Exception</h4>
      {summaryErr&&<div className="card" style={{border:'1px solid var(--red)',background:'#fff1f1',marginBottom:10}}><b className="err">{summaryErr}</b></div>}
      <form onSubmit={submitForm}>
        <div className="form-grid">
          <div className="form-field">
            <label>Document Number *</label>
            <input value={formData.docNo} onChange={e=>setFormData({...formData,docNo:e.target.value})} placeholder="e.g. INV-990011"/>
            {errMap.docNo&&<span className="err">{errMap.docNo}</span>}
          </div>
          <div className="form-field">
            <label>Supplier *</label>
            <input value={formData.supplier} onChange={e=>setFormData({...formData,supplier:e.target.value})} placeholder="e.g. Acme Supplies"/>
            {errMap.supplier&&<span className="err">{errMap.supplier}</span>}
          </div>
          <div className="form-field">
            <label>Company Code (4 digits) *</label>
            <input value={formData.companyCode} onChange={e=>setFormData({...formData,companyCode:e.target.value})} placeholder="e.g. 1400"/>
            {errMap.companyCode&&<span className="err">{errMap.companyCode}</span>}
          </div>
          <div className="form-field">
            <label>Plant (4 digits) *</label>
            <input value={formData.plant} onChange={e=>setFormData({...formData,plant:e.target.value})} placeholder="e.g. 1102"/>
            {errMap.plant&&<span className="err">{errMap.plant}</span>}
          </div>
          <div className="form-field">
            <label>Material *</label>
            <input value={formData.material} onChange={e=>setFormData({...formData,material:e.target.value})} placeholder="e.g. Steel Pipe SP-1"/>
            {errMap.material&&<span className="err">{errMap.material}</span>}
          </div>
          <div className="form-field">
            <label>Amount *</label>
            <input type="number" step="0.01" value={formData.amount} onChange={e=>setFormData({...formData,amount:e.target.value})} placeholder="e.g. 15000"/>
            {errMap.amount&&<span className="err">{errMap.amount}</span>}
          </div>
          <div className="form-field">
            <label>Currency</label>
            <input value={formData.currency} onChange={e=>setFormData({...formData,currency:e.target.value})} placeholder="GBP"/>
            {errMap.currency&&<span className="err">{errMap.currency}</span>}
          </div>
          <div className="form-field">
            <label>Exception Type *</label>
            <select value={formData.type} onChange={e=>setFormData({...formData,type:e.target.value})}>
              <option value="PRICE_MISMATCH">PRICE_MISMATCH</option>
              <option value="GR_MISSING">GR_MISSING</option>
              <option value="MISSING_MASTER_DATA">MISSING_MASTER_DATA</option>
              <option value="APPROVAL_PENDING">APPROVAL_PENDING</option>
              <option value="QTY_MISMATCH">QTY_MISMATCH</option>
            </select>
            {errMap.type&&<span className="err">{errMap.type}</span>}
          </div>
          <div className="form-field">
            <label>Status *</label>
            <select value={formData.status} onChange={e=>setFormData({...formData,status:e.target.value})}>
              <option value="OPEN">OPEN</option>
              <option value="AWAITING_APPROVAL">AWAITING_APPROVAL</option>
              <option value="RESOLVED">RESOLVED</option>
            </select>
            {errMap.status&&<span className="err">{errMap.status}</span>}
          </div>
          <div className="form-field">
            <label>Stuck Step *</label>
            <select value={formData.stuckStep} onChange={e=>setFormData({...formData,stuckStep:Number(e.target.value)})}>
              <option value={0}>0 - Purchase Order</option>
              <option value={1}>1 - Goods Receipt</option>
              <option value={2}>2 - Invoice</option>
              <option value={3}>3 - FI posting</option>
            </select>
            {errMap.stuckStep&&<span className="err">{errMap.stuckStep}</span>}
          </div>
          <div className="form-field">
            <label>Created Date (yyyy-MM-dd) *</label>
            <input type="date" value={formData.createdDate} onChange={e=>setFormData({...formData,createdDate:e.target.value})}/>
            {errMap.createdDate&&<span className="err">{errMap.createdDate}</span>}
          </div>
          <div className="form-field">
            <label>Due Date (yyyy-MM-dd) *</label>
            <input type="date" value={formData.dueDate} onChange={e=>setFormData({...formData,dueDate:e.target.value})}/>
            {errMap.dueDate&&<span className="err">{errMap.dueDate}</span>}
          </div>
          <div className="form-field">
            <label>Purchase Order #</label>
            <input value={formData.poNumber} onChange={e=>setFormData({...formData,poNumber:e.target.value})} placeholder="e.g. 4500010"/>
            {errMap.poNumber&&<span className="err">{errMap.poNumber}</span>}
          </div>
          <div className="form-field">
            <label>Goods Receipt #</label>
            <input value={formData.grNumber} onChange={e=>setFormData({...formData,grNumber:e.target.value})} placeholder="e.g. 5000010"/>
            {errMap.grNumber&&<span className="err">{errMap.grNumber}</span>}
          </div>
          <div className="form-field">
            <label>Invoice Number</label>
            <input value={formData.invoiceNumber} onChange={e=>setFormData({...formData,invoiceNumber:e.target.value})} placeholder="Defaults to Doc #"/>
            {errMap.invoiceNumber&&<span className="err">{errMap.invoiceNumber}</span>}
          </div>
          <div className="form-field">
            <label>FI Document #</label>
            <input value={formData.fiDocument} onChange={e=>setFormData({...formData,fiDocument:e.target.value})} placeholder="e.g. 1900010"/>
            {errMap.fiDocument&&<span className="err">{errMap.fiDocument}</span>}
          </div>
          <div className="form-field">
            <label>Resolved Date (yyyy-MM-dd)</label>
            <input type="date" value={formData.resolvedDate} onChange={e=>setFormData({...formData,resolvedDate:e.target.value})}/>
            {errMap.resolvedDate&&<span className="err">{errMap.resolvedDate}</span>}
          </div>
          <div className="form-field">
            <label>Owner</label>
            <input value={formData.owner} onChange={e=>setFormData({...formData,owner:e.target.value})} placeholder="e.g. Ben"/>
            {errMap.owner&&<span className="err">{errMap.owner}</span>}
          </div>
          <div className="form-field" style={{gridColumn:'1/-1'}}>
            <label>Root Cause</label>
            <input value={formData.rootCause} onChange={e=>setFormData({...formData,rootCause:e.target.value})} placeholder="e.g. Invoice price is 6% above PO price"/>
            {errMap.rootCause&&<span className="err">{errMap.rootCause}</span>}
          </div>
        </div>
        <div style={{display:'flex',gap:8,marginTop:12}}>
          <button type="submit" className="btn">Save exception</button>
          <button type="button" className="btn" style={{background:'var(--mut)'}} onClick={()=>{setShowForm(false);setFormErrors([]);setSummaryErr('')}}>Cancel</button>
        </div>
      </form>
    </div>}

    <div className="filters"><input placeholder="Search doc, supplier, material" onChange={e=>setF({...f,q:e.target.value})}/>
      {FILTERS.map(([key,field])=><select key={key} value={f[key]||''} onChange={e=>setF({...f,[key]:e.target.value})}>
        <option value="">{key}: all</option>{[...new Set(all.map(c=>c[field]).filter(Boolean))].map(o=><option key={o}>{o}</option>)}</select>)}
      <button className="btn" onClick={exportExcel}>Export to Excel</button>
      {exportErr&&<span className="err" style={{alignSelf:'center'}}>{exportErr}</span>}
    </div>
    <div className="tbl"><table><thead><tr>{['Document','Supplier','Plant','Amount','Type','Status','Owner','Due','Risk'].map(h=><th key={h}>{h}</th>)}</tr></thead>
      <tbody>{rows.map(c=><tr className="row" key={c.id} onClick={()=>open(c.id)}><td>{c.docNo}</td><td>{c.supplier}</td><td>{c.plant}</td><td>{money(c.amount)}</td>
        <td>{c.type}</td><td>{c.status}</td><td>{c.owner||'Unassigned'}</td><td>{c.dueDate}</td><td>{c.riskScore} <Tag v={c.riskLevel}/></td></tr>)}</tbody></table></div></>}

function Detail({id,api,back}){
  const [c,setC]=useState(null),[log,setLog]=useState([]),[owner,setOwner]=useState(''),[text,setText]=useState(''),[err,setErr]=useState('');
  const load=()=>{api('/exceptions/'+id).then(setC);api('/exceptions/'+id+'/audit').then(setLog)};
  useEffect(()=>{load()},[]);
  const act=(a,body,ask)=>{if(ask&&!window.confirm(ask))return;setErr('');
    api('/exceptions/'+id+'/'+a,{method:'POST',body:JSON.stringify(body||{})}).then(()=>{setText('');setOwner('');load()}).catch(e=>setErr(e.message))};
  if(!c)return <p>Loading…</p>;
  const steps=['Purchase Order','Goods Receipt','Supplier Invoice','FI Posting'],done=c.status==='RESOLVED';
  return <>
    <button className="btn" onClick={back}>← Back to queue</button>
    <h3>{c.docNo} · {c.supplier} · {money(c.amount)} <Tag v={c.riskLevel}/></h3>
    <div className="card"><h4>Process timeline</h4><div className="steps">{steps.map((s,i)=>
      <div key={s} className={done||i<c.stuckStep?'done':i===c.stuckStep?'stuck':''}>{s}{!done&&i===c.stuckStep&&<><br/>stuck here</>}</div>)}</div></div>
    <div className="two">
      <div className="card"><h4>SAP documents (mock)</h4>
        <p>Company code {c.companyCode} · Plant {c.plant} · Material {c.material}<br/>Purchase Order: {c.poNumber||'—'}<br/>Goods Receipt: {c.grNumber||'—'}<br/>
        Supplier Invoice: {c.invoiceNumber||'—'}<br/>FI Accounting Document: {c.fiDocument||'Not posted'}<br/>Due {c.dueDate} · Owner {c.owner||'Unassigned'} · {c.status}</p></div>
      <div className="card"><h4>Root cause and next action</h4><p><b>Root cause:</b> {c.rootCause}</p><p><b>Recommended:</b> {NEXT[c.type]}</p>
        <h4>Risk score {c.riskScore}/100 (rules shown)</h4><ul>{c.riskReasons.map(r=><li key={r}>{r}</li>)}</ul></div></div>
    {!done&&<div className="card"><h4>Actions</h4>
      <div className="row2"><input placeholder="Owner name" value={owner} onChange={e=>setOwner(e.target.value)}/><button className="btn" onClick={()=>act('assign',{owner})}>Assign owner</button></div>
      <div className="row2"><input placeholder="Add a comment" value={text} onChange={e=>setText(e.target.value)}/><button className="btn" onClick={()=>act('comment',{text})}>Add comment</button></div>
      <div className="row2"><button className="btn" onClick={()=>act('request-approval',{},'Request approval for this exception?')}>Request approval</button>
        <button className="btn red" onClick={()=>act('resolve',{text},'Resolve this exception? This is logged and cannot be undone.')}>Resolve</button></div>
      {err&&<p className="err">{err}</p>}</div>}
    <div className="card"><h4>Audit trail (read-only)</h4>{log.length===0&&<p>No actions yet.</p>}
      {log.map(l=><p key={l.id}>{l.ts.replace('T',' ').slice(0,16)} · {l.actor} ({l.role}) · <b>{l.action}</b>: {l.detail}</p>)}</div></>}

export default function App(){
  const [user,setUser]=useState(null),[page,setPage]=useState('queue'),[sel,setSel]=useState(null);
  if(!user)return <div className="login"><h2>BlueArc Invoice Exceptions</h2><p>Sign in as a demo user</p>
    {USERS.map(u=><button key={u.name} onClick={()=>setUser(u)}>{u.name} · {u.role}</button>)}</div>;
  const api=(p,o={})=>fetch('/api'+p,{...o,headers:{'Content-Type':'application/json','X-User':user.name,'X-Role':user.role}})
    .then(async r=>{if(!r.ok){const e=await r.json().catch(()=>({}));throw new Error(e.message||'Request failed')}return r.json()});
  return <div><header><b>BlueArc · MM/FI Exception Resolution</b>
    <nav>{['queue','analytics'].map(p=><button key={p} className={page===p?'on':''} onClick={()=>{setPage(p);setSel(null)}}>{p}</button>)}</nav>
    <span>{user.name} ({user.role}) <button onClick={()=>setUser(null)}>Log out</button></span></header>
    <main>{sel?<Detail id={sel} api={api} back={()=>setSel(null)}/>:page==='queue'?<Queue api={api} open={setSel} user={user}/>:<Analytics api={api}/>}</main></div>}
