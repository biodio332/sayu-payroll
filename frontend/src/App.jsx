import React, { useState } from 'react'
import axios from 'axios'

export default function App() {
  const [file, setFile] = useState(null)
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  async function upload() {
    setErrorMessage('')
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

  return (
    <div>
      <h2>Payroll Automation</h2>

      <input
        type="file"
        accept=".xls,.xlsx"
        onChange={(e) => setFile(e.target.files?.[0] || null)}
      />

      <div style={{ marginTop: 12 }}>
        <button onClick={upload} disabled={loading}>
          {loading ? 'Uploading...' : 'Upload & Calculate'}
        </button>

        <button
          onClick={downloadExcel}
          disabled={loading || rows.length === 0}
          style={{ marginLeft: 8 }}
        >
          Download Results (Excel)
        </button>
      </div>

      {errorMessage ? (
        <p style={{ color: 'red', marginTop: 12 }}>{errorMessage}</p>
      ) : null}

      {rows.length > 0 ? (
        <table border="1" cellPadding="6" cellSpacing="0" style={{ marginTop: 12 }}>
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
                <td>{r.totalHours}</td>
                <td>{r.regularPay}</td>
                <td>{r.overtimePay}</td>
                <td>{r.totalSalary}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </div>
  )
}

