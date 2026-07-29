import React, { useMemo, useState } from 'react';
import {
  Alert,
  BarChart,
  Badge,
  Button,
  Card,
  DataTable,
  EmptyState,
  Field,
  FormActions,
  FormGrid,
  Grid,
  MetricTile,
  PageHeader,
} from '../design-system';
import { api } from '../api.js';

function toDateInputValue(value) {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getDefaultWindow() {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - 6);
  return {
    from: toDateInputValue(from),
    to: toDateInputValue(to),
  };
}


export default function RejectionPatternsScreen() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [rows, setRows] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const hasRange = Boolean(from && to);

  const load = async (nextFrom = from, nextTo = to) => {
    if (!nextFrom || !nextTo) {
      setRows([]);
      setError(null);
      return;
    }
    setLoading(true);
    try {
      const data = await api.listReasonCodes(nextFrom, nextTo);
      setRows(data);
      setError(null);
    } catch (e) {
      setRows([]);
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  const applyRecentWindow = async () => {
    const nextWindow = getDefaultWindow();
    setFrom(nextWindow.from);
    setTo(nextWindow.to);
    await load(nextWindow.from, nextWindow.to);
  };

  const chartData = useMemo(
      () => rows.map((row) => ({
        label: row.code,
        value: row.count,
        tone: row.kind === 'review' ? 'info' : 'negative',
      })),
      [rows]);

  const reviewCount = rows.filter((row) => row.kind === 'review').reduce((sum, row) => sum + row.count, 0);
  const rejectionCount = rows.filter((row) => row.kind === 'rejection').reduce((sum, row) => sum + row.count, 0);

  const columns = [
    { key: 'code', header: 'Reason code', mono: true },
    {
      key: 'kind',
      header: 'Kind',
      tight: true,
      render: (row) => (
        <Badge tone={row.kind === 'review' ? 'info' : 'negative'}>{row.kind}</Badge>
      ),
    },
    { key: 'count', header: 'Count', numeric: true, tight: true, width: '6ch' },
  ];

  return (
    <>
      <PageHeader
        title="Rejection Patterns"
        lede="ranked reason code counts for a submitted-date window"
      />

      <Card>
        <FormGrid cols={2}>
          <Field label="From" hint="inclusive date">
            {({ id }) => (
              <input
                id={id}
                className="ds-input"
                type="date"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            )}
          </Field>
          <Field label="To" hint="inclusive date">
            {({ id }) => (
              <input
                id={id}
                className="ds-input"
                type="date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            )}
          </Field>
          <FormGrid.Full>
            <FormActions className="uc05-form-actions">
              <Button variant="secondary" onClick={applyRecentWindow} disabled={loading}>
                Recent 7 days
              </Button>
              <Button variant="primary" onClick={() => load()} disabled={!hasRange || loading}>
                {loading ? 'Loading...' : 'Apply window'}
              </Button>
            </FormActions>
          </FormGrid.Full>
        </FormGrid>
      </Card>

      {error && (
        <Alert tone="negative" title="Could not load reason-code counts">
          {error}
        </Alert>
      )}

      {!hasRange ? (
        <EmptyState title="Pick a from and to date" style={{ marginTop: 'var(--ds-space-6)' }}>
          Select both ends of the window to see ranked reason-code counts.
        </EmptyState>
      ) : (
        <>
          <Grid cols={3} min={180} style={{ marginTop: 'var(--ds-space-6)', marginBottom: 'var(--ds-space-6)' }}>
            <MetricTile label="Reason codes" value={rows.length} />
            <MetricTile label="Review hits" value={reviewCount} tone="info" />
            <MetricTile label="Rejection hits" value={rejectionCount} tone="negative" />
          </Grid>

          <div className="uc05-layout">
            <Card className="uc05-panel" title="Ranked bar chart">
              {chartData.length === 0 ? (
                <EmptyState title="No reason codes in this window" />
              ) : (
                <BarChart data={chartData} labelWidth="18rem" />
              )}
            </Card>
            <Card className="uc05-panel" title="Counts table">
              <DataTable
                columns={columns}
                rows={rows}
                rowKey={(row) => row.code}
                maxRows={10}
                total={rows.length}
                empty={<EmptyState title="No reason codes in this window" />}
              />
            </Card>
          </div>
        </>
      )}
    </>
  );
}
