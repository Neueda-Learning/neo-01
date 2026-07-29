import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  Caption,
  DataTable,
  EmptyState,
  PageHeader,
  Split,
} from '../design-system';
import { api } from '../api.js';
import NewProductVersionModal from './NewProductVersionModal.jsx';

export default function ProductConfigurationScreen() {
  const [selectedProductCode, setSelectedProductCode] = useState(null);
  const [productRows, setProductRows] = useState([]);
  const [versionHistory, setVersionHistory] = useState({});
  const [loadError, setLoadError] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [showNewVersionModal, setShowNewVersionModal] = useState(false);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  // Initial load on mount
  useEffect(() => {
    let cancelled = false;

    async function loadProducts() {
      try {
        const codes = await api.listProductCodes();
        const versionEntries = await Promise.all(
          codes.map(async (code) => [code, await api.getProductVersions(code)])
        );

        if (cancelled) return;

        const nextHistory = Object.fromEntries(
          versionEntries.map(([code, versions]) => [
            code,
            versions.map((entry) => ({
              version: `v${entry.version}`,
              meta: entry.current ? 'current' : String(entry.effectiveFrom).slice(0, 10),
              summary: `min ${entry.minAge} · ${entry.limitMin.toLocaleString('en-GB')}-${entry.limitMax.toLocaleString('en-GB')} · ${entry.active ? 'active' : 'inactive'}`,
              channels:
                entry.channels && entry.channels.length > 0
                  ? `channels ${entry.channels.join('/')}`
                  : null,
              current: entry.current,
            })),
          ])
        );

        const nextRows = versionEntries
          .map(([code, versions]) => {
            const current = versions.find((entry) => entry.current) ?? versions[versions.length - 1];
            if (!current) return null;
            return {
              productCode: code,
              version: `v${current.version}`,
              minAge: current.minAge,
              limitRange: `${current.limitMin.toLocaleString('en-GB')}-${current.limitMax.toLocaleString('en-GB')}`,
              active: current.active,
              channels:
                current.channels && current.channels.length > 0
                  ? current.channels.join('·').replaceAll('MOBILE_APP', 'MOB').replaceAll('BRANCH', 'BR')
                  : 'all',
            };
          })
          .filter(Boolean);

        setVersionHistory(nextHistory);
        setProductRows(nextRows.length > 0 ? nextRows : []);
        setSelectedProductCode((current) =>
          nextHistory[current] ? current : nextRows[0]?.productCode ?? null
        );
        setLoadError(null);
        setIsLoading(false);
      } catch (e) {
        if (cancelled) return;
        setProductRows([]);
        setVersionHistory({});
        setSelectedProductCode(null);
        setLoadError(e.message);
        setIsLoading(false);
      }
    }

    void loadProducts();

    return () => {
      cancelled = true;
    };
  }, []);

  // Refresh when manual refresh is triggered
  useEffect(() => {
    if (refreshTrigger === 0) return; // Skip initial state

    let cancelled = false;

    async function refreshProducts() {
      try {
        const codes = await api.listProductCodes();
        const versionEntries = await Promise.all(
          codes.map(async (code) => [code, await api.getProductVersions(code)])
        );

        if (cancelled) return;

        const nextHistory = Object.fromEntries(
          versionEntries.map(([code, versions]) => [
            code,
            versions.map((entry) => ({
              version: `v${entry.version}`,
              meta: entry.current ? 'current' : String(entry.effectiveFrom).slice(0, 10),
              summary: `min ${entry.minAge} · ${entry.limitMin.toLocaleString('en-GB')}-${entry.limitMax.toLocaleString('en-GB')} · ${entry.active ? 'active' : 'inactive'}`,
              channels:
                entry.channels && entry.channels.length > 0
                  ? `channels ${entry.channels.join('/')}`
                  : null,
              current: entry.current,
            })),
          ])
        );

        const nextRows = versionEntries
          .map(([code, versions]) => {
            const current = versions.find((entry) => entry.current) ?? versions[versions.length - 1];
            if (!current) return null;
            return {
              productCode: code,
              version: `v${current.version}`,
              minAge: current.minAge,
              limitRange: `${current.limitMin.toLocaleString('en-GB')}-${current.limitMax.toLocaleString('en-GB')}`,
              active: current.active,
              channels:
                current.channels && current.channels.length > 0
                  ? current.channels.join('·').replaceAll('MOBILE_APP', 'MOB').replaceAll('BRANCH', 'BR')
                  : 'all',
            };
          })
          .filter(Boolean);

        setVersionHistory(nextHistory);
        setProductRows(nextRows.length > 0 ? nextRows : []);
        setLoadError(null);
      } catch (e) {
        if (cancelled) return;
        setLoadError(e.message);
      }
    }

    void refreshProducts();

    return () => {
      cancelled = true;
    };
  }, [refreshTrigger]);

  const selectedHistory = useMemo(
    () => versionHistory[selectedProductCode] ?? [],
    [selectedProductCode, versionHistory]
  );

  const columns = [
    { key: 'productCode', header: 'Product', width: '34%' },
    { key: 'version', header: 'Ver', tight: true, width: '8%' },
    { key: 'minAge', header: 'Min age', tight: true, width: '12%' },
    { key: 'limitRange', header: 'Limit', width: '18%' },
    {
      key: 'active',
      header: 'Active',
      width: '11%',
      render: (row) => <Badge tone={row.active ? 'positive' : 'negative'}>{row.active ? 'YES' : 'NO'}</Badge>,
    },
    { key: 'channels', header: 'Channels', width: '17%' },
  ];

  return (
    <>
      <PageHeader
        title="Product Configuration"
        lede="Versioned thresholds — a new version per change, never an edit"
      />

      {isLoading && <Caption>Loading products...</Caption>}

      {loadError && (
        <Alert tone="warning" title="Failed to load products">
          {loadError}
        </Alert>
      )}

      {!isLoading && productRows.length === 0 && !loadError && (
        <EmptyState title="No products found">
          No product configuration is available.
        </EmptyState>
      )}

      {!isLoading && productRows.length > 0 && (
        <Split
          ratio="wide"
          sidebar={
            <Card
              title={`Version history — ${selectedProductCode}`}
              foot={
                <Caption>
                  Every case stores the version it decided with, so old decisions stay explainable.
                </Caption>
              }
            >
              <div className="product-config-history">
                {selectedHistory.map((entry) => (
                  <div key={`${selectedProductCode}-${entry.version}`} className="product-config-history__row">
                    <div className="product-config-history__meta">
                      {entry.version} · {entry.meta}
                    </div>
                    <div className="product-config-history__summary">{entry.summary}</div>
                    {entry.channels && (
                      <div className="product-config-history__channels">{entry.channels}</div>
                    )}
                  </div>
                ))}
              </div>
            </Card>
          }
        >
          <div className="product-config-main">
            <DataTable
              className="product-config-table"
              columns={columns}
              rows={productRows}
              total={productRows.length}
              rowKey={(row) => row.productCode}
              onRowClick={(row) => setSelectedProductCode(row.productCode)}
              selectedKey={selectedProductCode}
            />

            <div className="product-config-actions">
              <Button variant="primary" onClick={() => setShowNewVersionModal(true)}>
                New version...
              </Button>
            </div>
          </div>
        </Split>
      )}

      {showNewVersionModal && (
        <NewProductVersionModal
          productCode={selectedProductCode}
          onClose={() => setShowNewVersionModal(false)}
          onSuccess={() => {
            setRefreshTrigger((prev) => prev + 1);
          }}
        />
      )}
    </>
  );
}