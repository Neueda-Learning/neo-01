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

  // Calculate min and max values for color and font scaling
  const values = chartData.map((d) => d.value);
  const minValue = Math.min(...values, Infinity);
  const maxValue = Math.max(...values, -Infinity);
  const valueRange = maxValue - minValue || 1;

  // Add color depth and font size to each chart item, plus ranking
  const chartDataWithStyling = chartData.map((item, index) => {
    const normalized = (item.value - minValue) / valueRange;
    // Map normalized value (0-1) to color depth (0-4, where higher = darker)
    const colorDepth = Math.round(normalized * 4);
    
    // Font size for labels: base for top 3, smaller for others
    let fontSize;
    if (index === 0) {
      fontSize = 1.25; // Rank 1: largest
    } else if (index === 1) {
      fontSize = 1.125; // Rank 2: medium-large
    } else if (index === 2) {
      fontSize = 1.0; // Rank 3: medium
    } else {
      fontSize = 0.875; // Others: default
    }
    
    // Bar thickness: base height is 18px
    // Top 3 get thicker bars: 1st=28px, 2nd=24px, 3rd=20px
    let barHeight;
    if (index === 0) {
      barHeight = 28;
    } else if (index === 1) {
      barHeight = 24;
    } else if (index === 2) {
      barHeight = 20;
    } else {
      barHeight = 18;
    }
    
    return {
      ...item,
      colorDepth,
      fontSize,
      barHeight,
      rank: index + 1,
    };
  });

  // Get failures only for ranking in sidebar
  const failuresForRanking = data ? data.filter((item) => item.kind === 'failure') : [];

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
                {data.map((item) => {
                  // Determine font size based on rank for failures only
                  let fontSize = '0.9375rem';
                  let fontWeight = 400;
                  
                  if (item.kind === 'failure') {
                    const failureRank = failuresForRanking.indexOf(item);
                    if (failureRank === 0) {
                      fontSize = '1.125rem'; // Rank 1: largest
                      fontWeight = 600;
                    } else if (failureRank === 1) {
                      fontSize = '1.0625rem'; // Rank 2: medium-large
                      fontWeight = 550;
                    } else if (failureRank === 2) {
                      fontSize = '0.9875rem'; // Rank 3: medium-small
                      fontWeight = 500;
                    }
                  }
                  
                  return (
                    <div key={`${item.code}-${item.kind}`} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize, color: '#333', fontWeight }}>
                        {item.code}
                        {item.kind === 'review' && (
                          <span style={{ fontSize: '0.75rem', marginLeft: '0.5rem', color: '#999' }}>(review)</span>
                        )}
                      </span>
                      <span style={{ fontSize, fontWeight: 500, color: '#17a697' }}>
                        {item.count}
                      </span>
                    </div>
                  );
                })}
              </div>
            </Card>
          }
        >
          <Card title="Ranked failures by count">
            {chartDataWithStyling.length > 0 ? (
              <BarChart data={chartDataWithStyling} />
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
