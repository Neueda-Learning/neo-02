import React, { useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Card,
  DataTable,
  EmptyState,
  KeyValue,
  PageHeader,
  Split,
} from '../design-system';
import { api } from '../api.js';

/**
 * UC08 · View Config History
 *
 * Follows the same case-detail shape as the rest of the app (`Split`): the
 * version history board on the left, the read-only document for whichever
 * version is selected on the right — never editable here, that is UC07's
 * `POST /config`.
 */
export default function PolicyConfigScreen() {
  const [versions, setVersions] = useState([]);
  const [selectedVersion, setSelectedVersion] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.listConfigVersions()
      .then((data) => {
        setVersions(data);
        const current = data.find((v) => v.isCurrent) ?? data[data.length - 1] ?? null;
        setSelectedVersion(current?.version ?? null);
        setError(null);
      })
      .catch((e) => setError(e.message));
  }, []);

  const selected = versions.find((v) => v.version === selectedVersion) ?? null;

  const columns = [
    { key: 'version', header: 'Version', mono: true, render: (v) => `v${v.version}` },
    {
      key: 'status',
      header: 'Status',
      tight: true,
      render: (v) => (
        <Badge tone={v.isCurrent ? 'positive' : 'neutral'}>
          {v.isCurrent ? 'CURRENT' : 'superseded'}
        </Badge>
      ),
    },
    {
      key: 'effectiveFrom',
      header: 'Effective from',
      render: (v) => (v.effectiveFrom ? new Date(v.effectiveFrom).toLocaleString() : '—'),
    },
    { key: 'sampleEvery', header: 'Sample every', numeric: true },
  ];

  return (
    <>
      <PageHeader
        title="Policy Configuration"
        lede="every past version of the policy — oldest first · read-only · select a version to see the lists that decided a case under it"
      />

      {error && (
        <Alert tone="negative" title="Could not load policy configuration">
          {error} — the backend may still be starting.
        </Alert>
      )}

      <Split
        sidebar={
          <Card
            title={selected ? `Version ${selected.version}` : 'No version selected'}
            subtitle={selected ? (selected.isCurrent ? 'Current' : 'Superseded') : undefined}
            headEnd={selected?.isCurrent ? <Badge tone="positive">CURRENT</Badge> : undefined}
          >
            {!selected ? (
              <EmptyState title="Pick a version from the list to see its full document" />
            ) : (
              <>
                <KeyValue
                  items={[
                    {
                      label: 'Effective from',
                      value: selected.effectiveFrom
                        ? new Date(selected.effectiveFrom).toLocaleString()
                        : '—',
                    },
                    {
                      label: 'Supported residencies',
                      value: (selected.supportedResidencies ?? []).join(', ') || '—',
                    },
                    {
                      label: 'Excluded residencies',
                      value: (selected.excludedResidencies ?? []).join(', ') || '—',
                    },
                    { label: 'Sample every (X)', value: String(selected.sampleEvery) },
                  ]}
                />

                <h3 className="ds-card__title" style={{ marginTop: 'var(--ds-space-6)' }}>
                  Restriction list
                </h3>
                {(selected.restrictionList ?? []).length === 0 ? (
                  <EmptyState title="No restricted entries in this version" />
                ) : (
                  selected.restrictionList.map((entry, i) => (
                    <Card key={i} style={{ marginTop: 'var(--ds-space-4)' }}>
                      <KeyValue
                        items={[
                          { label: 'Full name', value: entry.fullName },
                          { label: 'Date of birth', value: entry.dateOfBirth },
                          { label: 'Reason', value: entry.reason },
                        ]}
                      />
                    </Card>
                  ))
                )}
              </>
            )}
          </Card>
        }
      >
        <DataTable
          columns={columns}
          rows={versions}
          rowKey={(v) => v.version}
          onRowClick={(v) => setSelectedVersion(v.version)}
          selectedKey={selectedVersion}
          footnote="oldest first"
          empty={<EmptyState title="No versions found" />}
        />
      </Split>
    </>
  );
}
