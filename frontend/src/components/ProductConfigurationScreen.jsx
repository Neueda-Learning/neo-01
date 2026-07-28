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

const MOCK_PRODUCT_ROWS = [
  {
    productCode: 'CREDIT_CARD_REWARDS',
    version: 'v4',
    minAge: 20,
    limitRange: '2,000-15,000',
    active: false,
    channels: 'WEB·BR',
  },
  {
    productCode: 'CREDIT_CARD_STANDARD',
    version: 'v1',
    minAge: 18,
    limitRange: '500-5,000',
    active: true,
    channels: 'WEB·MOB',
  },
  {
    productCode: 'CREDIT_CARD_STUDENT',
    version: 'v1',
    minAge: 18,
    limitRange: '500-3,000',
    active: true,
    channels: 'WEB·MOB',
  },
];

const MOCK_VERSION_HISTORY = {
  CREDIT_CARD_STANDARD: [
    {
      version: 'v1',
      meta: 'current',
      summary: 'min 18 · 500-5,000 · active',
      channels: 'channels WEB/MOBILE_APP',
      current: true,
    },
  ],
  CREDIT_CARD_REWARDS: [
    {
      version: 'v4',
      meta: 'current',
      summary: 'min 20 · 2,000-15,000 · inactive',
      channels: 'channels WEB/BRANCH',
      current: true,
    },
    {
      version: 'v3',
      meta: '2026-07-03',
      summary: 'min 19 · 1,500-12,000 · active',
      channels: 'channels WEB/MOBILE_APP/BRANCH/PHONE',
    },
    {
      version: 'v2',
      meta: '2026-07-02',
      summary: 'min 18 · 1,200-11,000 · active',
      channels: 'channels WEB/MOBILE_APP/BRANCH',
    },
    {
      version: 'v1',
      meta: '2026-07-01',
      summary: 'min 18 · 1,000-10,000 · active',
      channels: 'channels WEB/MOBILE_APP/BRANCH',
    },
  ],
  CREDIT_CARD_STUDENT: [
    {
      version: 'v1',
      meta: 'current',
      summary: 'min 18 · 500-3,000 · active',
      channels: 'channels WEB/MOBILE',
      current: true,
    },
  ],
};

export default function ProductConfigurationScreen() {
  const [selectedProductCode, setSelectedProductCode] = useState('CREDIT_CARD_REWARDS');
  const [productRows, setProductRows] = useState(MOCK_PRODUCT_ROWS);
  const [versionHistory, setVersionHistory] = useState(MOCK_VERSION_HISTORY);
  const [loadError, setLoadError] = useState(null);

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
        setProductRows(nextRows.length > 0 ? nextRows : MOCK_PRODUCT_ROWS);
        setSelectedProductCode((current) =>
          nextHistory[current] ? current : nextRows[0]?.productCode ?? 'CREDIT_CARD_REWARDS'
        );
        setLoadError(null);
      } catch (e) {
        if (cancelled) return;
        setProductRows(MOCK_PRODUCT_ROWS);
        setVersionHistory(MOCK_VERSION_HISTORY);
        setSelectedProductCode('CREDIT_CARD_REWARDS');
        setLoadError(e.message);
      }
    }

    void loadProducts();

    return () => {
      cancelled = true;
    };
  }, []);

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
            empty={<EmptyState title="No products found">No product configuration is available.</EmptyState>}
          />

          <div className="product-config-actions">
            <Button variant="primary">New version...</Button>
          </div>
        </div>
      </Split>
    </>
  );
}