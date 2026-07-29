import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  ChipGroup,
  DataTable,
  EmptyState,
  Grid,
  MetricTile,
  PageHeader,
  SearchInput,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { statusTone, STATUSES, time } from '../status.js';

const FILTERS = ['All', ...STATUSES];

/**
 * Everything this module has answered.
 *
 * UC00's durable intake board. A row appears as IN_PROGRESS as soon as the request commits.
 *
 * The board follows the platform shape (design-system/DESIGN.md § "Board"): a header stating the
 * screen's rules, a toolbar that narrows, and a paged table. DataTable keeps each page capped at
 * 10 rows; Previous/Next moves through the full durable intake board.
 */
export default function RequestsScreen({
  requests,
  error,
  info,
  page,
  more,
  total,
  allTotal,
  counts,
  filter,
  onPageChange,
  onFilterChange,
  onOpenCase,
}) {
  const [query, setQuery] = useState('');
  // null = not yet searched (show the polled page); [] or rows = backend search result
  const [searchResults, setSearchResults] = useState(null);

  // When query changes, call backend; clear results when query is wiped
  useEffect(() => {
    if (!query.trim()) {
      setSearchResults(null);
      return;
    }
    let cancelled = false;
    api.searchApplications(query.trim())
      .then((data) => { if (!cancelled) setSearchResults(data || []); })
      .catch(() => { if (!cancelled) setSearchResults([]); });
    return () => { cancelled = true; };
  }, [query]);

  // Source: backend search results when query is active, the current polled page otherwise
  const source = searchResults ?? requests;
  const pageCount = Math.max(1, Math.ceil(total / 10));

  const searchCounts = useMemo(
    () =>
      (searchResults ?? []).reduce((acc, r) => {
        acc[r.status] = (acc[r.status] ?? 0) + 1;
        return acc;
      }, {}),
    [searchResults]
  );
  const visibleCounts = searchResults == null ? counts : searchCounts;

  // Status chip filter applies to whatever source is active; no local text filter
  const matches = useMemo(() => {
    return source.filter((r) => filter === 'All' || r.status === filter);
  }, [source, filter]);

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'status',
      header: 'Status',
      tight: true,
      render: (r) => <Badge tone={statusTone(r.status)}>{r.status}</Badge>,
    },
    { key: 'reference', header: 'Reference', mono: true },
    { key: 'createdAt', header: 'Submitted', render: (r) => time(r.createdAt) },
  ];

  return (
    <>
      <PageHeader
        title="Applications"
        lede="everything the orchestrator has sent this module, and what it answered · newest first"
        meta={
          info
            ? `${info.serviceId} · ${info.domain} · v${info.version}` +
              (info.mockedDependencies?.length
                ? ` · mocking ${info.mockedDependencies.join(', ')}`
                : ' · nothing mocked')
            : undefined
        }
      />

      {error && (
        <Alert tone="negative" title="Could not load applications">
          {error} — the backend may still be starting. The list retries every two seconds.
        </Alert>
      )}

      <Grid cols={2} min={180} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Seen" value={allTotal} />
        <MetricTile label="In progress" value={counts.IN_PROGRESS ?? 0} tone="info" />
      </Grid>

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application id"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search applications"
        />
        <ChipGroup
          options={FILTERS}
          value={filter}
          onChange={onFilterChange}
          counts={visibleCounts}
        />
      </Toolbar>

      <DataTable
        columns={columns}
        rows={matches}
        total={matches.length}
        rowKey={(r) => r.applicationId}
        onRowClick={onOpenCase}
        footnote="newest first"
        empty={
          <EmptyState
            title={allTotal === 0 ? 'Nothing received yet' : 'No application matches that'}
          >
            {allTotal === 0 ? (
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

      {searchResults == null && (
        <Toolbar style={{ marginTop: 'var(--ds-space-4)' }}>
          <Button
            variant="ghost"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
          >
            Previous
          </Button>
          <span>Page {page + 1} of {pageCount}</span>
          <Button
            variant="ghost"
            disabled={!more}
            onClick={() => onPageChange(page + 1)}
          >
            Next
          </Button>
        </Toolbar>
      )}
    </>
  );
}
