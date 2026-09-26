import { render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import App from './App';

describe('Dashboard JSON Parsing/Rendering', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders WAITING FOR PHONE initially', () => {
    render(<App />);
    expect(screen.getByText('WAITING FOR PHONE')).toBeInTheDocument();
  });

  it('renders incident data when JSON is successfully parsed', async () => {
    const mockIncident = {
      timestamp: 1729000000000, // Fixed time for test
      location: "37.7749, -122.4194",
      alertStatus: "SENT",
      movementResult: "POST_CRASH_STILLNESS",
      summary: "Incident reported at 37.7749, -122.4194. Status: SENT. Movement: POST_CRASH_STILLNESS.",
      chainIntegrity: "INTACT"
    };

    global.fetch = vi.fn(() =>
      Promise.resolve({
        json: () => Promise.resolve({ status: 'OK', incident: mockIncident })
      })
    );

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('Rakshak Incident Dashboard')).toBeInTheDocument();
    });

    expect(screen.getAllByText(/37.7749, -122.4194/i)[0]).toBeInTheDocument();
    expect(screen.getAllByText(/POST_CRASH_STILLNESS/i)[0]).toBeInTheDocument();
    expect(screen.getByText(/INTACT/i)).toBeInTheDocument();
  });
});
