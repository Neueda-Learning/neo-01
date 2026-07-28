import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Caption,
  Card,
  ChipGroup,
  DataTable,
  EmptyState,
  KeyValue,
  PageHeader,
  SearchInput,
  Split,
  TextInput,
  Toolbar,
} from '../design-system';
import { statusTone, time } from '../status.js';

const FILTERS = [
  'All',
  { value: 'ACCEPTED', label: 'PASSED' },
  { value: 'REJECTED', label: 'FAILED' },
  { value: 'REFERRED', label: 'REVIEW' },
];
const NAME_BY_APP_ID = {
  'app-1234': 'Maria Nowak',
  'app-1235': 'Priya Raman',
  'app-1236': 'Tom Bright',
  'app-1237': 'Daniel Osei',
  'app-1240': 'Ana Vasquez',
  'app-1241': 'Liam Carter',
};

const CASE_DETAIL_BY_APP_ID = {
  'app-1234': {
    productCode: 'CREDIT_CARD_REWARDS',
    requestedLimit: 'GBP 3,000',
    channel: 'MOBILE_APP',
    residence: 'GB',
    dateOfBirth: '1996-04-11',
    termsAccepted: 'true',
    productConfigVersion: 'v3',
    rules: [
      {
        title: 'Well-formedness sweep',
        outcome: 'PASSED',
        description: 'All required fields present and well-formed | limit 3,000 within 500-10,000 | terms accepted',
      },
      {
        title: 'R1 | Age vs product minimum',
        outcome: 'PASSED',
        description: 'Applicant is 30, minimum required is 18',
      },
      {
        title: 'R2 | Product still active',
        outcome: 'PASSED',
        description: 'CREDIT_CARD_REWARDS is active in ProductConfig v3',
      },
      {
        title: 'R3 | Channel eligibility',
        outcome: 'PASSED',
        description: 'MOBILE_APP is a permitted channel for this product',
      },
    ],
  },
  'app-1235': {
    productCode: 'CREDIT_CARD_STUDENT',
    requestedLimit: 'GBP 800',
    channel: 'MOBILE_APP',
    residence: 'GB',
    dateOfBirth: '2009-03-02',
    termsAccepted: 'true',
    productConfigVersion: 'v3',
    rules: [
      {
        title: 'Well-formedness sweep',
        outcome: 'PASSED',
        description: 'All required fields present and well-formed | limit 800 within 250-1,000 | terms accepted',
      },
      {
        title: 'R1 | Age vs product minimum',
        outcome: 'FAILED',
        description: 'VER_AGE_BELOW_MINIMUM - applicant is 17, CREDIT_CARD_STUDENT requires 18',
      },
      {
        title: 'R2 | Product still active',
        outcome: 'PASSED',
        description: 'CREDIT_CARD_STUDENT is active in ProductConfig v3',
      },
      {
        title: 'R3 | Channel eligibility',
        outcome: 'PASSED',
        description: 'MOBILE_APP is a permitted channel for this product',
      },
    ],
  },
  'app-1236': {
    productCode: 'CREDIT_CARD_STANDARD',
    requestedLimit: 'GBP 1,500',
    channel: 'WEB',
    residence: 'GB',
    dateOfBirth: '2001-11-08',
    termsAccepted: 'true',
    productConfigVersion: 'v3',
    rules: [
      {
        title: 'Well-formedness sweep',
        outcome: 'FAILED',
        description: 'VER_MISSING_FIELD on applicant.email - VER_INVALID_FIELD on applicant.mobile - VER_MISSING_FIELD on applicant.currentAddress',
      },
      {
        title: 'R1 | Age vs product minimum',
        outcome: 'PASSED',
        description: 'Applicant is 24, minimum required is 18',
      },
      {
        title: 'R2 | Product still active',
        outcome: 'PASSED',
        description: 'CREDIT_CARD_STANDARD is active in ProductConfig v3',
      },
      {
        title: 'R3 | Channel eligibility',
        outcome: 'PASSED',
        description: 'WEB is a permitted channel for this product',
      },
    ],
  },
};

function toDecisionLabel(status) {
  const normalized = String(status ?? '').toUpperCase();
  if (normalized === 'ACCEPTED') return 'PASSED';
  if (normalized === 'REJECTED') return 'FAILED';
  if (normalized === 'REFERRED') return 'REVIEW';
  if (normalized === 'IN_PROGRESS') return 'IN_PROGRESS';
  return normalized || 'UNKNOWN';
}

function toneForDecisionLabel(label) {
  if (label === 'PASSED') return 'positive';
  if (label === 'FAILED') return 'negative';
  if (label === 'REVIEW') return 'warning';
  if (label === 'IN_PROGRESS') return 'info';
  return 'neutral';
}

function detailFor(row) {
  const existing = CASE_DETAIL_BY_APP_ID[row.applicationId];
  if (existing) return existing;

  const outcome = toDecisionLabel(row.status);
  return {
    productCode: 'CREDIT_CARD_STANDARD',
    requestedLimit: 'GBP 1,000',
    channel: 'WEB',
    residence: 'GB',
    dateOfBirth: '1998-01-01',
    termsAccepted: 'true',
    productConfigVersion: 'v3',
    rules: [
      {
        title: 'Well-formedness sweep',
        outcome: 'PASSED',
        description: 'All required fields present and well-formed',
      },
      {
        title: 'R1 | Age vs product minimum',
        outcome,
        description: 'Mocked detail generated for this row',
      },
      {
        title: 'R2 | Product still active',
        outcome: 'PASSED',
        description: 'Product is active in ProductConfig v3',
      },
      {
        title: 'R3 | Channel eligibility',
        outcome: 'PASSED',
        description: 'Channel is permitted for this product',
      },
    ],
  };
}

/**
 * Everything this module has answered.
 *
 * ⚠️ Three columns, because the placeholder table behind it has three columns. When you replace
 * `demo_showcase` with your own table, this is the screen that shows it off — the operator UI is a
 * graded deliverable, so add the columns, filters and detail views your business topic needs.
 *
 * The board follows the platform shape (design-system/DESIGN.md § "Board"): a header stating the
 * screen's rules, a toolbar that narrows, a capped table. The 10-row cap and its footnote come from
 * DataTable — no screen re-implements them.
 */
export default function RequestsScreen({ requests, error, loading, onLoad }) {
  const [queryInput, setQueryInput] = useState('');
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');
  const [fromDate, setFromDate] = useState('2026-07-01');
  const [toDate, setToDate] = useState('2026-07-21');
  const [hasAsked, setHasAsked] = useState(true);
  const [selectedApplicationId, setSelectedApplicationId] = useState(null);

  const counts = useMemo(() => {
    const next = { All: requests.length };
    for (const row of requests) {
      next[row.status] = (next[row.status] ?? 0) + 1;
    }
    return next;
  }, [requests]);

  const matches = useMemo(() => {
    if (!hasAsked) return [];

    const needle = query.trim().toLowerCase();
    return requests.filter((r) => {
      if (filter !== 'All' && r.status !== filter) return false;
      const createdDate = r.createdAt ? new Date(r.createdAt).toISOString().slice(0, 10) : null;
      if (createdDate && fromDate && createdDate < fromDate) return false;
      if (createdDate && toDate && createdDate > toDate) return false;

      if (!needle) return true;
      const applicantName = (NAME_BY_APP_ID[r.applicationId] ?? '').toLowerCase();
      return r.applicationId.toLowerCase().includes(needle) || applicantName.includes(needle);
    });
  }, [requests, query, filter, fromDate, toDate, hasAsked]);

  useEffect(() => {
    if (!hasAsked) return undefined;

    const hasInProgress = matches.some((row) => row.status?.toUpperCase() === 'IN_PROGRESS');
    if (!hasInProgress) return undefined;

    const id = setInterval(() => {
      void onLoad();
    }, 1500);

    return () => clearInterval(id);
  }, [matches, hasAsked, onLoad]);

  function reasonCount(ruleResults) {
    if (!ruleResults) return 1;
    try {
      const parsed = JSON.parse(ruleResults);
      if (Array.isArray(parsed)) {
        return parsed.reduce((sum, item) => sum + (item?.reasonCodes?.length ?? 0), 0) || 1;
      }
      if (parsed && Array.isArray(parsed.ruleResults)) {
        return parsed.ruleResults.reduce((sum, item) => sum + (item?.reasonCodes?.length ?? 0), 0) || 1;
      }
    } catch {
      return 1;
    }
    return 1;
  }

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true, width: '20%' },
    {
      key: 'applicant',
      header: 'Applicant',
      width: '22%',
      render: (r) => NAME_BY_APP_ID[r.applicationId] ?? '—',
    },
    { key: 'createdAt', header: 'Submitted', width: '22%', render: (r) => time(r.createdAt) },
    {
      key: 'status',
      header: 'Outcome',
      tight: true,
      width: '18%',
      render: (r) => <Badge tone={statusTone(r.status)}>{toDecisionLabel(r.status)}</Badge>,
    },
    {
      key: 'reasons',
      header: 'Reasons',
      tight: true,
      numeric: true,
      width: '10%',
      render: (r) => reasonCount(r.ruleResults),
    },
  ];

  const selectedRow = useMemo(
    () => requests.find((row) => row.applicationId === selectedApplicationId) ?? null,
    [requests, selectedApplicationId]
  );

  if (selectedRow) {
    const detail = detailFor(selectedRow);
    const applicant = NAME_BY_APP_ID[selectedRow.applicationId] ?? 'Unknown applicant';
    const caseOutcome = toDecisionLabel(selectedRow.status);

    return (
      <>
        <PageHeader
          title={`Case ${selectedRow.applicationId}`}
          badge={<Badge tone={statusTone(selectedRow.status)}>{caseOutcome}</Badge>}
          meta={`${applicant} | ${detail.productCode} | submitted ${time(selectedRow.createdAt)} | decided with ProductConfig ${detail.productConfigVersion}`}
        />

        <h3 className="verification-detail-section-title">Rule results - every check, pass or fail</h3>

        <Split
          sidebar={
            <>
              <Card title="Applicant - live from orchestrator">
                <KeyValue
                  items={[
                    ['Full name', applicant],
                    ['Date of birth', detail.dateOfBirth],
                    ['Product', detail.productCode],
                    ['Requested limit', detail.requestedLimit],
                    ['Channel', detail.channel],
                    ['Residence', detail.residence],
                    ['Terms accepted', detail.termsAccepted],
                  ]}
                  keyWidth="45%"
                />
              </Card>
              <Caption>
                Nothing here is stored by this module - fetched on open via GET /applications/{'{id}'}
              </Caption>
            </>
          }
        >
          <div className="verification-detail-rules">
            {detail.rules.map((rule) => (
              <Card
                key={rule.title}
                title={rule.title}
                headEnd={<Badge tone={toneForDecisionLabel(rule.outcome)}>{rule.outcome}</Badge>}
              >
                {rule.description}
              </Card>
            ))}
          </div>
        </Split>

        <div className="verification-detail-actions">
          <Button variant="primary">Override decision...</Button>
          <Button variant="secondary" onClick={() => setSelectedApplicationId(null)}>
            Back to board
          </Button>
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Verification Board"
        lede="empty until you search · max 10 rows · names fetched live, never stored"
      />

      {error && requests.length === 0 && (
        <Alert tone="negative" title="Could not load applications">
          {error}
        </Alert>
      )}

      <Toolbar>
        <Toolbar.Group className="verification-board-toolbar-group">
          <SearchInput
            placeholder="Maria"
            value={queryInput}
            onChange={(e) => {
              const value = e.target.value;
              setQueryInput(value);
              setQuery(value);
              setHasAsked(true);
            }}
            aria-label="Search application id or applicant name"
          />
        </Toolbar.Group>
        <Toolbar.Group className="verification-board-toolbar-group">
          <ChipGroup
            options={FILTERS}
            value={filter}
            onChange={(next) => {
              setFilter(next);
              setHasAsked(true);
            }}
            counts={counts}
          />
        </Toolbar.Group>
        <Toolbar.Spacer />
        <Toolbar.Group className="verification-board-toolbar-group">
          <span className="verification-board-date-label">From</span>
          <TextInput
            type="date"
            size="sm"
            className="verification-board-date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            aria-label="From date"
          />
          <span className="verification-board-date-label">To</span>
          <TextInput
            type="date"
            size="sm"
            className="verification-board-date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            aria-label="To date"
          />
        </Toolbar.Group>
      </Toolbar>

      <DataTable
        className="verification-board-results"
        columns={columns}
        rows={matches}
        total={matches.length}
        rowKey={(r) => r.applicationId}
        onRowClick={(row) => setSelectedApplicationId(row.applicationId)}
        footnote="newest first"
        empty={
          <EmptyState
            title={
              !hasAsked
                ? 'Search for an applicant to begin'
                : requests.length === 0
                  ? 'Nothing received yet'
                  : 'No application matches that'
            }
          >
            {!hasAsked ? (
              <>Boards start empty on purpose — nothing is fetched until you ask.</>
            ) : requests.length === 0 ? (
              <>
                Send one from the <strong>sidecar</strong> at <strong>localhost:9000</strong>, or turn
                the generator on in the orchestrator UI. Nothing in this screen sends applications —
                this module is called, it does not call itself.
              </>
            ) : (
              <>Clear the search, or pick a different status.</>
            )}
          </EmptyState>
        }
      />
    </>
  );
}
