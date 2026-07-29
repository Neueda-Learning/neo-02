import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  DataTable,
  EmptyState,
  Field,
  PageHeader,
  TextInput,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';
import { statusTone, time } from '../status.js';

const POLL_MS = 5000;

export default function ReferralQueueScreen({ onOpenCase }) {
  const [rows, setRows] = useState([]);
  const [operator, setOperator] = useState(() => localStorage.getItem('policyOperator') ?? '');
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState(null);
  const [operatorError, setOperatorError] = useState(null);

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      setRows(await api.listReferrals());
      setError(null);
    } catch (e) {
      setError(e.message);
    } finally {
      if (!quiet) setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const id = setInterval(() => load(true), POLL_MS);
    return () => clearInterval(id);
  }, [load]);

  const requireOperator = () => {
    const value = operator.trim();
    if (!value) {
      setOperatorError('Enter your operator ID before claiming or releasing a case.');
      return null;
    }
    setOperatorError(null);
    localStorage.setItem('policyOperator', value);
    return value;
  };

  const act = async (row, action) => {
    const currentOperator = requireOperator();
    if (!currentOperator) return;
    setBusyId(row.applicationId);
    setError(null);
    try {
      if (action === 'claim') {
        await api.claimReferral(row.applicationId, currentOperator);
      } else {
        await api.releaseReferral(row.applicationId, currentOperator);
      }
      await load(true);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  };

  const columns = [
    { key: 'reference', header: 'Case', mono: true },
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'referralCause',
      header: 'Referral cause',
      render: (row) => <Badge tone="warning">{row.referralCause}</Badge>,
    },
    {
      key: 'machineOutcome',
      header: 'Machine outcome',
      render: (row) => (
        <Badge tone={statusTone(row.machineOutcome)}>{row.machineOutcome ?? '—'}</Badge>
      ),
    },
    {
      key: 'claimedBy',
      header: 'Claim state',
      render: (row) => row.claimedBy ? `Claimed by ${row.claimedBy}` : 'Unclaimed',
    },
    { key: 'submittedAt', header: 'Submitted', render: (row) => time(row.submittedAt) },
    {
      key: 'action',
      header: 'Action',
      tight: true,
      render: (row) => {
        const owned = row.claimedBy === operator.trim();
        return (
          <Button
            size="sm"
            variant={row.claimedBy ? 'ghost' : 'secondary'}
            disabled={Boolean(row.claimedBy) && !owned}
            busy={busyId === row.applicationId}
            onClick={(event) => {
              event.stopPropagation();
              act(row, owned ? 'release' : 'claim');
            }}
          >
            {owned ? 'Release' : row.claimedBy ? 'Locked' : 'Claim'}
          </Button>
        );
      },
    },
  ];

  return (
    <>
      <PageHeader
        title="Referral Queue"
        lede="open referrals only · unclaimed first · oldest first · max 10"
        actions={<Button onClick={() => load()}>Refresh</Button>}
      />

      {error && <Alert tone="negative" title="Referral action failed">{error}</Alert>}

      <Toolbar>
        <Field label="Operator ID" required error={operatorError}>
          {({ id, invalid, describedBy }) => (
            <TextInput
              id={id}
              invalid={invalid}
              aria-describedby={describedBy}
              value={operator}
              placeholder="e.g. s.chen"
              onChange={(event) => {
                setOperator(event.target.value);
                setOperatorError(null);
              }}
            />
          )}
        </Field>
      </Toolbar>

      {loading ? (
        <EmptyState title="Loading referral queue…" />
      ) : (
        <DataTable
          columns={columns}
          rows={rows}
          rowKey={(row) => row.applicationId}
          onRowClick={onOpenCase}
          footnote="unclaimed first · oldest first"
          empty={(
            <EmptyState title="No open referrals">
              Referred cases will appear here when machine processing parks a journey.
            </EmptyState>
          )}
        />
      )}
    </>
  );
}
