import React, { useMemo, useState } from 'react'
import axios from 'axios'
import './styles.css'

import sayuLogo from './Logo/sayu-logo.jpg'

export default function App() {
  const [file, setFile] = useState(null)
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [processingComplete, setProcessingComplete] = useState(false)

  async function upload() {
    setErrorMessage('')
    setProcessingComplete(false)
    setRows([])

    if (!file) {
      setErrorMessage('Please choose an Excel file (.xlsx) first.')
      return
    }

    const lowerName = (file.name || '').toLowerCase()
    if (!lowerName.endsWith('.xlsx') && !lowerName.endsWith('.xls')) {
      setErrorMessage('Invalid file type. Only .xls/.xlsx are supported.')
      return
    }

    setLoading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)

      const res = await axios.post('/api/payroll/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })

      setRows(res.data || [])
      setProcessingComplete(true)
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Upload failed.'
      setErrorMessage(msg)
    } finally {
      setLoading(false)
    }
  }

  async function downloadExcel() {
    if (!file) return
    setErrorMessage('')
    setProcessingComplete(false)

    setLoading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)

      const res = await axios.post('/api/payroll/upload/export', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        responseType: 'blob',
      })

      const blob = new Blob([res.data], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      })
      const url = window.URL.createObjectURL(blob)

      const a = document.createElement('a')
      a.href = url
      a.download = 'payroll-results.xlsx'
      document.body.appendChild(a)
      a.click()
      a.remove()

      window.URL.revokeObjectURL(url)
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Download failed.'
      setErrorMessage(msg)
    } finally {
      setLoading(false)
    }
  }

  const totals = useMemo(() => {
    const sumHours = rows.reduce((acc, r) => acc + (Number(r.totalHours) || 0), 0)
    const sumRegular = rows.reduce((acc, r) => acc + (Number(r.regularPay) || 0), 0)
    const sumOt = rows.reduce((acc, r) => acc + (Number(r.overtimePay) || 0), 0)
    const sumTotal = rows.reduce((acc, r) => acc + (Number(r.totalSalary) || 0), 0)
    return { sumHours, sumRegular, sumOt, sumTotal }
  }, [rows])

  const fmtHours = (v) => (Number.isFinite(v) ? v.toFixed(2) : '0.00')
  const fmtMoney = (v) =>
    new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP' }).format(Number(v) || 0)

  return (
    <div>
      <header className="topbar">
        <div className="topbarInner">
          <img className="sayuLogo" src={sayuLogo} alt="sayu" />
          <div className="topTitle">Payroll Automation System</div>
        </div>
      </header>

      <main className="container">
        <section className="card">
          <h2>Upload Biometrics Data</h2>
          <p className="subtitle">Upload your Excel file to automatically calculate employee wages</p>

          <div className="uploadBox">
            <div className="uploadIconCircle">⤴</div>

            <div className="chooseRow">
              <input
                className="fileInput"
                id="fileInput"
                type="file"
                accept=".xls,.xlsx"
                onChange={(e) => setFile(e.target.files?.[0] || null)}
              />
              <label className="chooseBtn" htmlFor="fileInput">
                Choose File
              </label>
            </div>

            <div className="uploadHint">Excel files only (.xlsx, .xls)</div>
          </div>

          <div style={{ marginTop: 14, display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            <button className="primaryBtn" onClick={upload} disabled={loading || !file}>
              {loading ? 'Processing...' : 'Upload & Calculate'}
            </button>

            <button
              className="downloadBtn"
              onClick={downloadExcel}
              disabled={loading || rows.length === 0}
            >
              Download Excel
            </button>
          </div>

          {errorMessage ? <div className="dangerText">{errorMessage}</div> : null}
        </section>

        {processingComplete && rows.length > 0 ? (
          <div className="successBanner">
            <span>✓</span>
            <span>Your payroll calculations are ready below.</span>
          </div>
        ) : null}

        {rows.length > 0 ? (
          <section className="card">
            <div className="resultsHeader">
              <div>
                <h2 style={{ marginBottom: 4 }}>Payroll Results</h2>
                <div className="employeesCount">{rows.length} employees processed</div>
              </div>
            </div>

            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Total Hours</th>
                  <th>Regular Pay</th>
                  <th>OT Pay</th>
                  <th>Total Salary</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.name}>
                    <td>{r.name}</td>
                    <td>{fmtHours(r.totalHours)}</td>
                    <td>{fmtMoney(r.regularPay)}</td>
                    <td>{fmtMoney(r.overtimePay)}</td>
                    <td>{fmtMoney(r.totalSalary)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="totalRow">
                  <td>Total</td>
                  <td>{fmtHours(totals.sumHours)}</td>
                  <td>{fmtMoney(totals.sumRegular)}</td>
                  <td>{fmtMoney(totals.sumOt)}</td>
                  <td>{fmtMoney(totals.sumTotal)}</td>
                </tr>
              </tfoot>
            </table>
          </section>
        ) : null}
      </main>

      <footer className="footer">© 2026 SAYU Cafe - Payroll Automation System</footer>
    </div>
  )
}

