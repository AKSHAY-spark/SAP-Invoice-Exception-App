# BlueArc Invoice Exception Resolution (React + Spring Boot + PostgreSQL)
1. Database: `createdb invoice_exceptions` (user postgres / password postgres, or edit backend/src/main/resources/application.properties)
2. Backend: `cd backend && mvn spring-boot:run`  (runs on :8080, seeds mock SAP data on first start)
3. Frontend: `cd frontend && npm install && npm run dev`  (open http://localhost:5173)
Flow: login -> dashboard KPIs + queue -> open SRV-460034 -> timeline, SAP docs, risk rules, actions, audit trail -> Analytics tab.
