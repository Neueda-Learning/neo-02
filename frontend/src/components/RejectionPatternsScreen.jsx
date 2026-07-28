import React, { useMemo, useState } from 'react';
import {
  Alert,
  BarChart,
  DataTable,
  EmptyState,
  Grid,
  MetricTile,
  PageHeader,
  TextInput,
  Toolbar,
  Button,
} from '../design-system';
import { api } from '../api.js';

const DEFAULT_FROM = '2026-07-01';
const DEFAULT_TO = '2026-07-14';

export default function RejectionPatternsScreen({ info }) {
  const [from, setFrom] = useState(DEFAULT_FROM);
  const [to, setTo] = useState(DEFAULT_TO);
  const [rows, setRows] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const result = await api.reasonCodes(from, to);
      setRows(result ?? []);
      setLoaded(true);
    } catch (e) {
      setRows([]);
      setLoaded(true);
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  const reviewTotal = useMemo(
    () => rows.filter((r) => r.kind === 'review').reduce((sum, r) => sum + r.count, 0),
    [rows]
  );

  const rejectionTotal = useMemo(
    () => rows.filter((r) => r.kind === 'rejection').reduce((sum, r) => sum + r.count, 0),
    [rows]
  );

  const bars = rows.map((r) => ({
    label: r.code,
    value: r.count,
    tone: r.kind === 'review' ? 'warning' : 'negative',
  }));

  const columns = [
    { key: 'code', header: 'Reason code', mono: true },
    { key: 'kind', header: 'Kind', tight: true },
    { key: 'count', header: 'Count', numeric: true, tight: true },
  ];

  return (
    <>
      <PageHeader
        title="Rejection Patterns"
        lede="ranked reason-code counts for a submittedAt window"
        meta={
          info
            ? `${info.serviceId} · ${info.domain} · v${info.version}` +
              (info.mockedDependencies?.length
                ? ` · mocking ${info.mockedDependencies.join(', ')}`
                : ' · nothing mocked')
            : undefined
        }
      />

      <Toolbar>
        <TextInput
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          aria-label="From date"
        />
        <TextInput
          type="date"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          aria-label="To date"
        />
        <Button onClick={load} disabled={loading || !from || !to}>
          {loading ? 'Loading…' : 'Apply'}
        </Button>
      </Toolbar>

      {error && (
        <Alert tone="negative" title="Could not load reason-code counts">
          {error}
        </Alert>
      )}

      {loaded && rows.length > 0 && (
        <>
          <Grid cols={2} min={180} style={{ marginBottom: 'var(--ds-space-6)' }}>
            <MetricTile label="Review hits" value={reviewTotal} tone="warning" />
            <MetricTile label="Rejection hits" value={rejectionTotal} tone="negative" />
          </Grid>

          <BarChart data={bars} labelWidth="22rem" style={{ marginBottom: 'var(--ds-space-6)' }} />

          <DataTable
            columns={columns}
            rows={rows}
            rowKey={(r) => r.code}
            total={rows.length}
            footnote="ranked descending by count"
          />
        </>
      )}

      {loaded && !loading && rows.length === 0 && (
        <EmptyState title="No reason codes in this window">
          The endpoint returns an empty list for date ranges with no matching submissions.
        </EmptyState>
      )}

      {!loaded && !loading && (
        <EmptyState title="Pick a date window and apply">
          Use from/to to load ranked rejection and review reasons.
        </EmptyState>
      )}
    </>
  );
}
