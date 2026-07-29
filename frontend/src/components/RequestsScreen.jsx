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
import { api } from '../api.js';
import { statusTone, time } from '../status.js';
import OverrideModal from './OverrideModal.jsx';

const FILTERS = [
  'All',
  { value: 'PASSED', label: 'PASSED' },
  { value: 'FAILED', label: 'FAILED' },
  { value: 'REVIEW', label: 'REVIEW' },
];

function toDecisionLabel(status) {
  const normalized = String(status ?? '').toUpperCase();
  if (normalized === 'PASSED') return 'PASSED';
  if (normalized === 'FAILED') return 'FAILED';
  if (normalized === 'REVIEW') return 'REVIEW';
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

function normalizeRuleResults(ruleResults) {
  if (Array.isArray(ruleResults)) return ruleResults;
  if (Array.isArray(ruleResults?.ruleResults)) return ruleResults.ruleResults;
  return [];
}

function mapCaseDetail(payload) {
  const ruleResults = normalizeRuleResults(payload?.ruleResults);
  const rules = ruleResults.map((rule, index) => {
    const reasons = Array.isArray(rule?.reasonCodes)
      ? rule.reasonCodes.filter((item) => typeof item === 'string' && item.trim())
      : [];
    return {
      title: rule?.ruleName ?? `Rule ${index + 1}`,
      outcome: rule?.passed === true ? 'PASSED' : rule?.passed === false ? 'FAILED' : 'UNKNOWN',
      reasons: reasons.length > 0 ? reasons : ['No reason supplied'],
    };
  });

  return {
    outcome: payload?.outcome ?? null,
    reference: payload?.reference ?? null,
    productConfigVersion: payload?.productConfigVersion ?? null,
    rules,
  };
}

function mapLiveApplicant(payload) {
  const app = payload?.application ?? payload;
  const applicant = app?.applicant ?? {};
  const product = app?.product ?? {};
  const consents = app?.consents ?? {};

  return {
    fullName: applicant?.fullName ?? null,
    dateOfBirth: applicant?.dateOfBirth ?? null,
    productCode: product?.productCode ?? null,
    requestedLimit:
      typeof product?.requestedCreditLimit === 'number'
        ? `GBP ${product.requestedCreditLimit.toLocaleString('en-GB')}`
        : null,
    channel: app?.channel ?? null,
    residence: applicant?.countryOfResidence ?? null,
    termsAccepted:
      typeof consents?.termsAccepted === 'boolean' ? String(consents.termsAccepted) : null,
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
export default function RequestsScreen({ requests, more, error, loading, onLoad, counts }) {
  const [queryInput, setQueryInput] = useState('');
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [hasAsked, setHasAsked] = useState(false);
  const [selectedApplicationId, setSelectedApplicationId] = useState(null);
  const [caseDetail, setCaseDetail] = useState({ status: 'idle', data: null, error: null });
  const [liveApplicant, setLiveApplicant] = useState({ status: 'idle', data: null, error: null });
  const [showOverrideModal, setShowOverrideModal] = useState(false);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  useEffect(() => {
    if (requests.length > 0) {
      setHasAsked(true);
    }
  }, [requests.length]);

  // First: filter by query and date (ignoring status tab) to get all matching results
  const allMatches = useMemo(() => {
    if (!hasAsked) return [];

    const needle = query.trim().toLowerCase();
    return requests.filter((r) => {
      const createdDate = r.createdAt ? new Date(r.createdAt).toISOString().slice(0, 10) : null;
      if (createdDate && fromDate && createdDate < fromDate) return false;
      if (createdDate && toDate && createdDate > toDate) return false;

      if (!needle) return true;
      const applicantName = (r.fullName ?? '').toLowerCase();
      return r.applicationId.toLowerCase().includes(needle) || applicantName.includes(needle);
    });
  }, [requests, query, fromDate, toDate, hasAsked]);

  // Use backend-provided total counts (from DB, independent of search filter)
  const badgeCounts = useMemo(() => {
    if (counts && Object.keys(counts).length > 0) {
      return counts;
    }
    // Fallback: compute from filtered results (e.g. when backend doesn't provide counts)
    const next = { All: allMatches.length };
    for (const row of allMatches) {
      const outcome = toDecisionLabel(row.status);
      next[outcome] = (next[outcome] ?? 0) + 1;
    }
    return next;
  }, [counts, allMatches]);

  // Then: use results as-is (backend already filtered by status)
  const matches = useMemo(() => {
    return allMatches;
  }, [allMatches]);

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
      render: (r) => r.fullName ?? '—',
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
      render: (r) => (typeof r.reasonCount === 'number' ? r.reasonCount : reasonCount(r.ruleResults)),
    },
  ];

  const selectedRow = useMemo(
    () => requests.find((row) => row.applicationId === selectedApplicationId) ?? null,
    [requests, selectedApplicationId]
  );

  useEffect(() => {
    if (!selectedRow) {
      setCaseDetail({ status: 'idle', data: null, error: null });
      setLiveApplicant({ status: 'idle', data: null, error: null });
      return;
    }

    let cancelled = false;
    setCaseDetail({ status: 'loading', data: null, error: null });
    setLiveApplicant({ status: 'loading', data: null, error: null });

    api
      .getCaseDetail(selectedRow.applicationId)
      .then((payload) => {
        if (cancelled) return;
        setCaseDetail({ status: 'ready', data: mapCaseDetail(payload), error: null });
      })
      .catch((e) => {
        if (cancelled) return;
        setCaseDetail({ status: 'error', data: null, error: e?.message ?? 'Unavailable' });
      });

    api
      .getApplicantFromOrchestrator(selectedRow.applicationId)
      .then((payload) => {
        if (cancelled) return;
        setLiveApplicant({ status: 'ready', data: mapLiveApplicant(payload), error: null });
      })
      .catch((e) => {
        if (cancelled) return;
        setLiveApplicant({ status: 'error', data: null, error: e?.message ?? 'Unavailable' });
      });

    return () => {
      cancelled = true;
    };
  }, [selectedRow]);

  if (selectedRow) {
    const detail = caseDetail.data;
    const applicant = selectedRow.fullName ?? 'Unknown applicant';
    const caseOutcome = detail?.outcome
      ? toDecisionLabel(detail.outcome)
      : toDecisionLabel(selectedRow.status);

    const liveData = liveApplicant.data;
    const applicantItems = [
      ['Full name', liveData?.fullName ?? applicant],
      ['Date of birth', liveData?.dateOfBirth ?? '—'],
      ['Product', liveData?.productCode ?? '—'],
      ['Requested limit', liveData?.requestedLimit ?? '—'],
      ['Channel', liveData?.channel ?? '—'],
      ['Residence', liveData?.residence ?? '—'],
      ['Terms accepted', liveData?.termsAccepted ?? '—'],
    ];

    return (
      <>
        <PageHeader
          title={`Case ${selectedRow.applicationId}`}
          badge={<Badge tone={statusTone(selectedRow.status)}>{caseOutcome}</Badge>}
          meta={`${applicant} | submitted ${time(selectedRow.createdAt)} | reference ${detail?.reference ?? '—'} | ProductConfig v${detail?.productConfigVersion ?? '—'}`}
        />

        <h3 className="verification-detail-section-title">Rule results - every check, pass or fail</h3>

        <Split
          sidebar={
            <>
              <Card title="Applicant - live from orchestrator">
                <KeyValue
                  items={applicantItems}
                  keyWidth="45%"
                />
              </Card>
              {liveApplicant.status === 'loading' && (
                <Caption>Loading applicant from orchestrator...</Caption>
              )}
              {liveApplicant.status === 'error' && (
                <Caption>
                  Orchestrator unavailable right now - applicant live data cannot be loaded.
                </Caption>
              )}
              <Caption>
                Nothing here is stored by this module - fetched on open via GET /cases/{'{id}'} and GET /applications/{'{id}'}
              </Caption>
            </>
          }
        >
          <div className="verification-detail-rules">
            {caseDetail.status === 'loading' && <Caption>Loading case detail...</Caption>}
            {caseDetail.status === 'error' && (
              <Alert tone="negative" title="Could not load case detail">
                {caseDetail.error}
              </Alert>
            )}
            {caseDetail.status === 'ready' && detail?.rules?.length === 0 && (
              <EmptyState title="No rule results">This case has no rule results recorded.</EmptyState>
            )}
            {caseDetail.status === 'ready' &&
              detail?.rules?.map((rule) => (
                <Card
                  key={rule.title}
                  title={rule.title}
                  headEnd={<Badge tone={toneForDecisionLabel(rule.outcome)}>{rule.outcome}</Badge>}
                >
                  {Array.isArray(rule.reasons) && rule.reasons.length > 0 ? (
                    <div>
                      {rule.reasons.map((reason, idx) => (
                        <div key={idx} style={{ marginBottom: idx < rule.reasons.length - 1 ? '8px' : '0' }}>
                          {reason}
                        </div>
                      ))}
                    </div>
                  ) : (
                    'No reason supplied'
                  )}
                </Card>
              ))}
          </div>
        </Split>

        <div className="verification-detail-actions">
          <Button variant="primary" onClick={() => setShowOverrideModal(true)}>
            Override decision...
          </Button>
          <Button variant="secondary" onClick={() => setSelectedApplicationId(null)}>
            Back to board
          </Button>
        </div>

        {showOverrideModal && (
          <OverrideModal
            applicationId={selectedRow.applicationId}
            currentOutcome={caseOutcome}
            onClose={() => setShowOverrideModal(false)}
            onSuccess={() => {
              // Refresh case detail after successful override
              setRefreshTrigger((prev) => prev + 1);
              void onLoad(query, filter);
            }}
          />
        )}
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
        <Alert tone="negative" title="Could not load cases">
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
              void onLoad(value, filter);
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
              void onLoad(query, next);
            }}
            counts={badgeCounts}
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

      <div style={{ display: 'grid', gap: 'var(--ds-space-4)' }}>
        <DataTable
          className="verification-board-results"
          columns={columns}
          rows={matches.slice(0, 10)}
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
        {badgeCounts[filter] > 10 && (
          <div style={{ display: 'flex', justifyContent: 'flex-end', paddingRight: 'var(--ds-space-4)' }}>
            <Caption style={{ color: 'var(--ds-color-text-muted)' }}>
              showing 10 of {badgeCounts[filter]} · more in database
            </Caption>
          </div>
        )}
      </div>
    </>
  );
}
