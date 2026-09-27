import { useState, useEffect } from 'react';
import jsPDF from 'jspdf';
import './App.css';

function App() {
  const [incidentData, setIncidentData] = useState(null);
  const [status, setStatus] = useState('WAITING_FOR_PHONE');

  useEffect(() => {
    const fetchIncident = async () => {
      try {
        const response = await fetch('http://localhost:3001/api/incident');
        const data = await response.json();
        
        if (data.status === 'OK' && data.incident) {
          setIncidentData(data.incident);
          setStatus('CONNECTED');
        } else {
          setStatus('WAITING_FOR_PHONE');
          setIncidentData(null);
        }
      } catch (err) {
        setStatus('WAITING_FOR_PHONE');
        setIncidentData(null);
      }
    };

    fetchIncident();
    const interval = setInterval(fetchIncident, 2000);
    return () => clearInterval(interval);
  }, []);

  const exportPDF = () => {
    if (!incidentData) return;
    const doc = new jsPDF();
    doc.setFontSize(20);
    doc.text('Incident Report', 20, 20);
    doc.setFontSize(12);
    doc.text(`Timestamp: ${new Date(incidentData.timestamp).toLocaleString()}`, 20, 40);
    doc.text(`Location: ${incidentData.location}`, 20, 50);
    doc.text(`Alert Status: ${incidentData.alertStatus}`, 20, 60);
    doc.text(`Movement Result: ${incidentData.movementResult}`, 20, 70);
    doc.text(`Summary: ${incidentData.summary}`, 20, 80);
    doc.text(`Chain Integrity: ${incidentData.chainIntegrity}`, 20, 90);
    doc.save('incident_report.pdf');
  };

  if (status === 'WAITING_FOR_PHONE' || !incidentData) {
    return (
      <div className="waiting-container">
        <div className="pulse-ring"></div>
        <h1 className="waiting-text">WAITING FOR PHONE</h1>
        <p className="waiting-subtext">Listening for Rakshak telemetry on port 3001...</p>
      </div>
    );
  }

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <div>
          <h1 className="brand-title">RAKSHAK</h1>
          <p className="brand-subtitle">INCIDENT COMMAND CENTER</p>
        </div>
        <div className="status-badge connected">
          <span className="dot"></span> TELEMETRY ACTIVE
        </div>
      </header>

      <div className="dashboard-grid">
        <div className="card glass-card hero-card">
          <div className="card-header">
            <h2>AI INCIDENT SUMMARY</h2>
            <button onClick={exportPDF} className="export-btn">EXPORT PDF</button>
          </div>
          <div className="terminal-box">
            <p className="terminal-text">{incidentData.summary}</p>
          </div>
        </div>

        <div className="card glass-card details-card">
          <h2>TELEMETRY DETAILS</h2>
          <ul className="details-list">
            <li>
              <span className="label">TIMESTAMP</span>
              <span className="value">{new Date(incidentData.timestamp).toLocaleString()}</span>
            </li>
            <li>
              <span className="label">LOCATION</span>
              <span className="value">{incidentData.location}</span>
            </li>
            <li>
              <span className="label">ALERT STATUS</span>
              <span className="value status-value">{incidentData.alertStatus}</span>
            </li>
            <li>
              <span className="label">MOVEMENT</span>
              <span className="value">{incidentData.movementResult}</span>
            </li>
            <li>
              <span className="label">INTEGRITY</span>
              <span className="value hash-value">{incidentData.chainIntegrity.substring(0, 16)}...</span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}

export default App;
