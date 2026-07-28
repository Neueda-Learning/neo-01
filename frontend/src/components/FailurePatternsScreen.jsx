import React, { useEffect, useState } from 'react';
import {
  Alert,
  BarChart,
  Button,
  Caption,
  Card,
  DataTable,
  EmptyState,
  PageHeader,
  Split,
  TextInput,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';

/**
 * UC-04: View Failure Patterns
 *
 * Shows ranked reason codes over a date window, with a bar chart and detailed counts table.
 * Helps identify if a rule is too strict or a form is broken.
 */
export default function FailurePatternsScreen() {
  const [fromDate, setFromDate] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 14);
    return d.toISOString().split('T')[0];
  });
  const [toDate, setToDate] = useState(() => new Date().toISOString().split('T')[0]);

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  async function handleApply() {
    if (!fromDate || !toDate) {
      setError('Both dates are required');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const result = await api.reasonCodeCounts(fromDate, toDate);
      setData(Array.isArray(result) ? result : []);
    } catch (e) {
      setError(e.message || 'Failed to load failure patterns');
      setData(null);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    // Auto-load on mount
    void handleApply();
  }, []);

  // Prepare data for bar chart
  const chartData = data
    ? data
        .filter((item) => item.kind !== 'review') // Only failures for chart
        .slice(0, 10) // Top 10 for readability
        .map((item) => ({
          label: item.code.replace('VER_', ''),
          value: item.count,
        }))
    : [];

  // Columns for detailed table
  const columns = [
    { key: 'code', header: 'Code', width: '40%', mono: true },
    { key: 'count', header: 'Count', width: '20%', numeric: true },
    {
      key: 'kind',
      header: 'Kind',
      width: '20%',
      render: (row) => (
        <span style={{ fontSize: '0.875rem', color: row.kind === 'failure' ? '#a61e4d' : '#a67c00' }}>
          {row.kind.toUpperCase()}
        </span>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Failure Patterns"
        lede="Which check fails most often — is a rule too strict?"
      />

      <Toolbar>
        <Toolbar.Group className="failure-patterns-toolbar">
          <TextInput
            type="date"
            size="sm"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            aria-label="From date"
          />
          <span style={{ color: '#666', fontSize: '0.875rem', marginX: '0.5rem' }}>To</span>
          <TextInput
            type="date"
            size="sm"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            aria-label="To date"
          />
          <Button variant="primary" onClick={handleApply} disabled={loading}>
            Apply
          </Button>
        </Toolbar.Group>
      </Toolbar>

      {error && <Alert tone="negative" title="Could not load patterns">{error}</Alert>}

      {loading && <Caption>Loading failure patterns...</Caption>}

      {!loading && data && data.length === 0 && (
        <EmptyState title="No failures in this window">
          Try a different date range or send more applications.
        </EmptyState>
      )}

      {!loading && data && data.length > 0 && (
        <Split
          sidebar={
            <Card title="Counts — failures vs review flags">
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                {data.map((item) => (
                  <div key={`${item.code}-${item.kind}`} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: '0.9375rem', color: '#333' }}>
                      {item.code}
                      {item.kind === 'review' && (
                        <span style={{ fontSize: '0.75rem', marginLeft: '0.5rem', color: '#999' }}>(review)</span>
                      )}
                    </span>
                    <span style={{ fontSize: '0.9375rem', fontWeight: 500, color: '#17a697' }}>
                      {item.count}
                    </span>
                  </div>
                ))}
              </div>
            </Card>
          }
        >
          <Card title="Ranked failures by count">
            {chartData.length > 0 ? (
              <BarChart data={chartData} />
            ) : (
              <EmptyState title="No failures to chart">
                This window contains only review-flag codes.
              </EmptyState>
            )}
          </Card>
        </Split>
      )}
    </>
  );
}
