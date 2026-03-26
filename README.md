# sayu-payroll

## MVP: Biometric Attendance -> Payroll Summary

### What it does
- Upload a biometric attendance `.xlsx` export
- Parse `Name`, `First Duty Time (Time In)`, `Off Duty Time (Time Out)`
- Compute hours worked (overnight shifts handled)
- Compute salary using a fixed hourly rate (`100`)
- Return grouped results as JSON and display them in a React table
- Optional: download the computed results back as an Excel file

---

## Run backend (Spring Boot)
1. In `backend/`, build & run:
   - `mvn clean package`
   - `mvn spring-boot:run`
2. Backend runs at `http://localhost:8080`

API:
- `POST /api/payroll/upload` (multipart form field name: `file`)
- `POST /api/payroll/upload/export` (same input, returns `.xlsx`)

---

## Run frontend (React)
1. In `frontend/`, install dependencies:
   - `npm install`
2. Start the dev server:
   - `npm run dev`
3. Frontend runs at `http://localhost:5173`
