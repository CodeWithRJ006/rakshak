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
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', background: '#222', color: '#fff' }}>
        <h1>WAITING FOR PHONE</h1>
      </div>
    );
  }

  return (
    <div style={{ padding: '20px', fontFamily: 'sans-serif', maxWidth: '600px', margin: '0 auto' }}>
      <h1>Rakshak Incident Dashboard</h1>
      <div style={{ background: '#f5f5f5', padding: '20px', borderRadius: '8px' }}>
        <p><strong>Timestamp:</strong> {new Date(incidentData.timestamp).toLocaleString()}</p>
        <p><strong>Location:</strong> {incidentData.location}</p>
        <p><strong>Alert Status:</strong> {incidentData.alertStatus}</p>
        <p><strong>Movement Result:</strong> {incidentData.movementResult}</p>
        <p><strong>Summary:</strong> {incidentData.summary}</p>
        <p><strong>Chain Integrity:</strong> {incidentData.chainIntegrity}</p>
      </div>
      <button 
        onClick={exportPDF} 
        style={{ marginTop: '20px', padding: '10px 20px', fontSize: '16px', background: '#007bff', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
      >
        Export PDF
      </button>
    </div>
  );
}

export default App;
