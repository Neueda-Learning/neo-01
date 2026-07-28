import React, { useCallback, useState } from 'react';
import { AppShell, EmptyState, TopNav } from './design-system';
import RequestsScreen from './components/RequestsScreen.jsx';
import { api } from './api.js';

const MOCK_REQUESTS = [
  {
    applicationId: 'app-1234',
    status: 'ACCEPTED',
    createdAt: '2026-07-21T21:40:00Z',
    ruleResults: JSON.stringify([{ reasonCodes: ['VER_ALL_CHECKS_PASSED'] }]),
  },
  {
    applicationId: 'app-1235',
    status: 'REJECTED',
    createdAt: '2026-07-21T18:02:00Z',
    ruleResults: JSON.stringify([{ reasonCodes: ['VER_AGE_BELOW_MINIMUM'] }]),
  },
  {
    applicationId: 'app-1236',
    status: 'REJECTED',
    createdAt: '2026-07-21T17:15:00Z',
    ruleResults: JSON.stringify([
      { reasonCodes: ['VER_MISSING_FIELD'] },
      { reasonCodes: ['VER_INVALID_FIELD'] },
      { reasonCodes: ['VER_MISSING_FIELD'] },
    ]),
  },
  {
    applicationId: 'app-1237',
    status: 'ACCEPTED',
    createdAt: '2026-07-21T16:48:00Z',
    ruleResults: JSON.stringify([{ reasonCodes: ['VER_ALL_CHECKS_PASSED'] }]),
  },
  {
    applicationId: 'app-1240',
    status: 'REFERRED',
    createdAt: '2026-07-21T15:31:00Z',
    ruleResults: JSON.stringify([{ reasonCodes: ['VER_AGE_EXACT_MINIMUM'] }]),
  },
  {
    applicationId: 'app-1241',
    status: 'ACCEPTED',
    createdAt: '2026-07-21T14:12:00Z',
    ruleResults: JSON.stringify([{ reasonCodes: ['VER_ALL_CHECKS_PASSED'] }]),
  },
];

const SCREENS = [
  { id: 'verification-board', label: 'Verification Board' },
  { id: 'failure-patterns', label: 'Failure Patterns' },
  { id: 'product-configuration', label: 'Product Configuration' },
];

export default function App() {
  const [screen, setScreen] = useState('verification-board');
  const [requests, setRequests] = useState(MOCK_REQUESTS);
  const [loadingRequests, setLoadingRequests] = useState(false);
  const [error, setError] = useState(null);

  const reload = useCallback(async () => {
    setLoadingRequests(true);
    try {
      const rows = await api.listApplications();
      setRequests(rows.length > 0 ? rows : MOCK_REQUESTS);
      setError(null);
    } catch (e) {
      setError(e.message);
      setRequests(MOCK_REQUESTS);
    } finally {
      setLoadingRequests(false);
    }
  }, []);

  return (
    <AppShell
      nav={<TopNav brand="NEO" product="Verification" tabs={SCREENS} active={screen} onSelect={setScreen} />}
      wide
    >
      {screen === 'verification-board' && (
        <RequestsScreen
          requests={requests}
          error={error}
          loading={loadingRequests}
          onLoad={reload}
        />
      )}

      {screen === 'failure-patterns' && (
        <EmptyState title="Failure Patterns">
          This screen will show ranked reason codes by date window (UC04).
        </EmptyState>
      )}

      {screen === 'product-configuration' && (
        <EmptyState title="Product Configuration">
          This screen will manage product versions and history (UC06 and UC07).
        </EmptyState>
      )}
    </AppShell>
  );
}
