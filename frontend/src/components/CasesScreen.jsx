import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  DataTable,
  EmptyState,
  PageHeader,
  SearchInput,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { time } from '../status.js';

/**
 * UC-01: Search Cases
 *
 * Operator board for searching applications by ID or applicant name. Results show
 * outcome, sampling status, and reason count extracted from policy evaluation.
 */
export default function CasesScreen({ info, onOpenCase }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [more, setMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [searched, setSearched] = useState(false);
  const [applicantCache, setApplicantCache] = useState({});
  const inFlightApplicants = useRef(new Set());

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!query.trim()) {
      setResults([]);
      setMore(false);
      setSearched(false);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const { results: rows, more: hasMore } = await api.searchCases(query.trim(), 10);
      setResults(rows || []);
      setMore(hasMore);
      setSearched(true);
    } catch (err) {
      setError(err.message);
      setResults([]);
      setMore(false);
      setSearched(true);
    } finally {
      setLoading(false);
    }
  };

  const outcomeToTone = (outcome) => {
    if (!outcome) return 'neutral';
    if (outcome === 'APPROVED') return 'positive';
    if (outcome === 'REJECTED') return 'negative';
    if (outcome === 'REFERRED') return 'warning';
    return 'neutral';
  };

  const fetchApplicant = (applicationId) => {
    if (inFlightApplicants.current.has(applicationId)) {
      return;
    }

    inFlightApplicants.current.add(applicationId);
    setApplicantCache((prev) => ({
      ...prev,
      [applicationId]: { name: '…', loading: true, retryable: false },
    }));

    api.getApplicant(applicationId)
      .then((payload) => {
        const raw = payload?.applicantName;
        const name = typeof raw === 'string' && raw.trim() ? raw : '—';
        setApplicantCache((prev) => ({
          ...prev,
          [applicationId]: { name, loading: false, retryable: false },
        }));
      })
      .catch((err) => {
        const retryable = err?.status === 503 || err?.details?.retryable === true;
        setApplicantCache((prev) => ({
          ...prev,
          [applicationId]: { name: '—', loading: false, retryable },
        }));
      })
      .finally(() => {
        inFlightApplicants.current.delete(applicationId);
      });
  };

  // Live-hydrate applicant names for current rows only (at most 10 rows per page view).
  useEffect(() => {
    if (!searched || results.length === 0) {
      return;
    }

    const ids = results.slice(0, 10).map((r) => r.applicationId);
    ids.forEach((id) => {
      if (applicantCache[id] == null) {
        fetchApplicant(id);
      }
    });
  }, [results, searched, applicantCache]);

  const retryApplicant = (applicationId) => {
    setApplicantCache((prev) => {
      const next = { ...prev };
      delete next[applicationId];
      return next;
    });
    fetchApplicant(applicationId);
  };

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'applicantName',
      header: 'Applicant',
      render: (r) => {
        const entry = applicantCache[r.applicationId];
        if (!entry || entry.loading) {
          return '…';
        }
        if (entry.retryable) {
          return (
            <span>
              —{' '}
              <Button
                size="sm"
                variant="ghost"
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  retryApplicant(r.applicationId);
                }}
              >
                Retry
              </Button>
            </span>
          );
        }
        return entry.name;
      },
    },
    {
      key: 'outcome',
      header: 'Outcome',
      tight: true,
      render: (r) =>
        r.outcome ? (
          <Badge tone={outcomeToTone(r.outcome)}>{r.outcome}</Badge>
        ) : (
          <Badge tone="neutral">—</Badge>
        ),
    },
    {
      key: 'sampled',
      header: 'Sampled',
      tight: true,
      render: (r) => (r.sampled ? <Badge tone="info">Yes</Badge> : '—'),
    },
    {
      key: 'reasonCount',
      header: 'Reasons',
      tight: true,
      render: (r) => (r.reasonCount > 0 ? `${r.reasonCount}` : '—'),
    },
    { key: 'submittedAt', header: 'Submitted', render: (r) => time(r.submittedAt) },
  ];

  return (
    <>
      <PageHeader
        title="Cases"
        lede="empty until you search · max 10 rows · names fetched live, never stored"
        meta={
          info
            ? `${info.serviceId} · custom ${info.domain} · v${info.version}`
            : undefined
        }
      />

      {error && (
        <Alert tone="negative" title="Search failed">
          {error}
        </Alert>
      )}

      <form onSubmit={handleSearch}>
        <Toolbar>
          <SearchInput
            placeholder="Search by application ID or applicant name…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onSubmit={handleSearch}
          />
        </Toolbar>
      </form>

      {loading && <EmptyState title="Searching…" />}

      {searched && !loading && results.length === 0 && (
        <EmptyState title="No matches found">
          Try a different application ID or name.
        </EmptyState>
      )}

      {results.length > 0 && (
        <DataTable
          columns={columns}
          rows={results}
          rowKey={(row) => row.applicationId}
          total={more ? results.length + 1 : results.length}
          onRowClick={onOpenCase}
          footnote="newest first"
        />
      )}

      {!searched && !loading && (
        <EmptyState title="Enter a search query">
          Search by application ID (e.g., "SIM-01") or applicant name (e.g., "Maria").
        </EmptyState>
      )}
    </>
  );
}
