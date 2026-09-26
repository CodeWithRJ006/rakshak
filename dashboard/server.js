const express = require('express');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

let latestIncident = null;
let lastSeen = null;

app.post('/api/incident', (req, res) => {
    latestIncident = req.body;
    lastSeen = Date.now();
    console.log("Received incident:", latestIncident);
    res.status(200).send({ status: 'OK' });
});

app.get('/api/incident', (req, res) => {
    if (!latestIncident) {
        return res.status(200).json({ status: 'WAITING_FOR_PHONE' });
    }
    // Optional: timeout logic if phone hasn't POSTed recently
    if (Date.now() - lastSeen > 60000) {
       // return res.status(200).json({ status: 'WAITING_FOR_PHONE' });
    }
    res.json({
        status: 'OK',
        incident: latestIncident
    });
});

const PORT = 3001;
app.listen(PORT, () => {
    console.log(`Server listening on port ${PORT}`);
});
