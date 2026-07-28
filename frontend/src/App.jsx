import React, { useCallback, useEffect, useState } from 'react';
import { AppShell, EmptyState, TopNav } from './design-system';
import ProductConfigurationScreen from './components/ProductConfigurationScreen.jsx';
import RequestsScreen from './components/RequestsScreen.jsx';
import FailurePatternsScreen from './components/FailurePatternsScreen.jsx';
import { api } from './api.js';

const SCREENS = [
  { id: 'verification-board', label: 'Verification Board' },
  { id: 'failure-patterns', label: 'Failure Patterns' },
  { id: 'product-configuration', label: 'Product Configuration' },
];

export default function App() {
  const [screen, setScreen] = useState('verification-board');
  const [requests, setRequests] = useState([]);
  const [loadingRequests, setLoadingRequests] = useState(false);
  const [error, setError] = useState(null);
  const [boardMore, setBoardMore] = useState(false);

  const reload = useCallback(async (query = '') => {
    setLoadingRequests(true);
    try {
      const result = await api.searchCases(query, 10);
      const rows = Array.isArray(result?.cases) ? result.cases : [];
      setRequests(
        rows.map((row) => ({
          applicationId: row.applicationId,
          fullName: row.fullName,
          status: row.outcome,
          createdAt: row.submittedAt,
          reasonCount: row.reasonCount,
          ruleResults: null,
        }))
      );
      setBoardMore(Boolean(result?.more));
      setError(null);
    } catch (e) {
      setError(e.message);
      setRequests([]);
      setBoardMore(false);
    } finally {
      setLoadingRequests(false);
    }
  }, []);

  useEffect(() => {
    if (screen !== 'verification-board') return;
    void reload('');
  }, [screen, reload]);

  return (
    <AppShell
      nav={<TopNav brand="NEO" product="Verification" tabs={SCREENS} active={screen} onSelect={setScreen} />}
      wide
    >
      {screen === 'verification-board' && (
        <RequestsScreen
          requests={requests}
          more={boardMore}
          error={error}
          loading={loadingRequests}
          onLoad={reload}
        />
      )}

      {screen === 'failure-patterns' && (
        <FailurePatternsScreen />
      )}

      {screen === 'product-configuration' && (
        <ProductConfigurationScreen />
      )}
    </AppShell>
  );
}
