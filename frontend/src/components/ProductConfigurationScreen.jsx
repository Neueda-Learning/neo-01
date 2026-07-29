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

function HistoryField({ label, value }) {
  return (
    <div className="product-config-history__field">
      <span className="product-config-history__field-label">{label}</span>
      <span className="product-config-history__field-value">{value}</span>
    </div>
  );
}

export default function ProductConfigurationScreen() {
  const [selectedProductCode, setSelectedProductCode] = useState(null);
  const [productRows, setProductRows] = useState([]);
  const [versionHistory, setVersionHistory] = useState({});
  const [loadError, setLoadError] = useState(null);
  const [showNewVersionModal, setShowNewVersionModal] = useState(false);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

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
              minAge: entry.minAge,
              creditLimitMin: entry.limitMin,
              creditLimitMax: entry.limitMax,
              active: entry.active,
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
        setProductRows(nextRows);
        setSelectedProductCode((current) =>
          nextHistory[current] ? current : nextRows[0]?.productCode ?? null
        );
        setLoadError(null);
      } catch (e) {
        if (cancelled) return;
        setProductRows([]);
        setVersionHistory({});
        setSelectedProductCode(null);
        setLoadError(e.message);
      }
    }

    void loadProducts();

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

        {loadError && (
          <Alert tone="warning" title="Using mock product configuration data">
            {loadError}
          </Alert>
        )}

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
                <div key={`${selectedProductCode}-${entry.version}`} className="product-config-history__card">
                  <div className="product-config-history__card-version">
                    {entry.version} · {entry.meta}
                  </div>
                  <div className="product-config-history__card-content">
                    <div className="product-config-history__row-1">
                      <HistoryField label="Age" value={entry.minAge} />
                      <HistoryField
                        label="Credit Limit Min"
                        value={entry.creditLimitMin?.toLocaleString('en-GB')}
                      />
                    </div>
                    <div className="product-config-history__row-2">
                      <HistoryField
                        label="Credit Limit Max"
                        value={entry.creditLimitMax?.toLocaleString('en-GB')}
                      />
                      <HistoryField label="Active" value={entry.active ? 'Yes' : 'No'} />
                    </div>
                    {entry.channels && (
                      <div className="product-config-history__channels-box">
                        <span className="product-config-history__field-label">Channels</span>
                        <div className="product-config-history__channels">{entry.channels}</div>
                      </div>
                    )}
                  </div>
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
            empty={<EmptyState title="No products found">No product configuration is available.</EmptyState>}
          />

          <div className="product-config-actions">
            <Button variant="primary" onClick={() => setShowNewVersionModal(true)}>
              New version...
            </Button>
          </div>
        </div>
      </Split>

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