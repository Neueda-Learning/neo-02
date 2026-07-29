import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  DataTable,
  EmptyState,
  KeyValue,
  PageHeader,
  Split,
  Stack,
} from '../design-system';
import { api } from '../api.js';
import PolicyConfigEditor from './PolicyConfigEditor.jsx';

/**
 * UC07 · Edit Policy Config + UC08 · View Config History
 *
 * Follows the same case-detail shape as the rest of the app (`Split`): the
 * version history board on the left, the read-only document for whichever
 * version is selected on the right. UC07 starts from the current document and
 * publishes the edited whole document as a new insert-only version.
 */
export default function PolicyConfigScreen() {
  const [versions, setVersions] = useState([]);
  const [selectedVersion, setSelectedVersion] = useState(null);
  const [compareMode, setCompareMode] = useState(false);
  const [compareWithVersion, setCompareWithVersion] = useState(null);
  const [restrictionSearchQuery, setRestrictionSearchQuery] = useState('');
  const [error, setError] = useState(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [publishedVersion, setPublishedVersion] = useState(null);

  const loadVersions = useCallback((preferredVersion = null) => {
    api.listConfigVersions()
      .then((data) => {
        setVersions(data);
        const current = data.find((v) => v.isCurrent) ?? data[data.length - 1] ?? null;
        setSelectedVersion((previous) => {
          if (preferredVersion != null && data.some((v) => v.version === preferredVersion)) {
            return preferredVersion;
          }
          if (previous != null && data.some((v) => v.version === previous)) return previous;
          return current?.version ?? null;
        });
        setError(null);
      })
      .catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    loadVersions();
  }, [loadVersions]);

  const selected = versions.find((v) => v.version === selectedVersion) ?? null;
  const current = versions.find((v) => v.isCurrent) ?? versions[versions.length - 1] ?? null;
  const compareWith = compareMode ? versions.find((v) => v.version === compareWithVersion) : null;

  // 检测差异的辅助函数
  const detectDifference = (valueA, valueB) => {
    if (Array.isArray(valueA) && Array.isArray(valueB)) {
      const aStr = JSON.stringify([...valueA].sort());
      const bStr = JSON.stringify([...valueB].sort());
      if (aStr === bStr) return 'same';
      const added = valueA.filter(v => !valueB.includes(v));
      const removed = valueB.filter(v => !valueA.includes(v));
      return { added, removed };
    }
    return valueA === valueB ? 'same' : { old: valueB, new: valueA };
  };

  // 生成对比摘要
  const generateComparisonSummary = () => {
    if (!selected || !compareWith) return null;

    const supportedDiff = detectDifference(selected.supportedResidencies, compareWith.supportedResidencies);
    const excludedDiff = detectDifference(selected.excludedResidencies, compareWith.excludedResidencies);
    const restrictionDiff = selected.restrictionList.length - compareWith.restrictionList.length;
    const sampleEveryChanged = selected.sampleEvery !== compareWith.sampleEvery;

    const changes = [];
    if (supportedDiff !== 'same' && supportedDiff.added) changes.push(`+ ${supportedDiff.added.length} country(ies)`);
    if (supportedDiff !== 'same' && supportedDiff.removed) changes.push(`- ${supportedDiff.removed.length} country(ies)`);
    if (excludedDiff !== 'same' && excludedDiff.added) changes.push(`+ ${excludedDiff.added.length} excluded(ies)`);
    if (excludedDiff !== 'same' && excludedDiff.removed) changes.push(`- ${excludedDiff.removed.length} excluded(ies)`);
    if (restrictionDiff !== 0) changes.push(`Restriction list: ${restrictionDiff > 0 ? '+' : ''}${restrictionDiff}`);
    if (sampleEveryChanged) changes.push(`Sample every: ${compareWith.sampleEvery} → ${selected.sampleEvery}`);

    return {
      supportedDiff,
      excludedDiff,
      restrictionDiff,
      sampleEveryChanged,
      changes,
      totalChanges: changes.length,
    };
  };

  const comparison = generateComparisonSummary();

  // 高亮文本组件
  const DiffText = ({ value, status }) => {
    if (status === 'added') {
      return <span style={{ color: '#10b981', fontWeight: '600' }}>✅ {value}</span>;
    }
    if (status === 'removed') {
      return <span style={{ color: '#ef4444', fontWeight: '600', textDecoration: 'line-through' }}>❌ {value}</span>;
    }
    return <span>{value}</span>;
  };

  // 显示数组差异
  const renderArrayWithDiff = (array, diffInfo) => {
    if (diffInfo === 'same' || !array) return array?.join(', ') || '—';
    if (Array.isArray(array)) {
      return array.map((item) => {
        let status = 'same';
        if (diffInfo.added?.includes(item)) status = 'added';
        if (diffInfo.removed?.includes(item)) status = 'removed';
        return (
          <div key={item} style={{ marginBottom: '4px' }}>
            <DiffText value={item} status={status} />
          </div>
        );
      });
    }
    return array?.join(', ') || '—';
  };

  // 过滤限制清单
  const filterRestrictions = (restrictions) => {
    if (!restrictionSearchQuery.trim()) return restrictions;
    const query = restrictionSearchQuery.toLowerCase();
    return restrictions.filter(
      (entry) =>
        entry.fullName.toLowerCase().includes(query) ||
        entry.reason.toLowerCase().includes(query) ||
        entry.dateOfBirth.toLowerCase().includes(query)
    );
  };

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
        lede="publish policy as data · every change creates a new version · earlier cases keep their original version"
        actions={
          <Button
            variant="primary"
            disabled={!current}
            onClick={() => {
              setPublishedVersion(null);
              setEditorOpen(true);
            }}
          >
            Edit Policy Config
          </Button>
        }
      />

      {publishedVersion != null && (
        <Alert
          tone="positive"
          title={`Policy version ${publishedVersion} is now current`}
          action={
            <Button size="sm" variant="ghost" onClick={() => setPublishedVersion(null)}>
              Dismiss
            </Button>
          }
        >
          It will be used by the next application; existing decisions remain unchanged.
        </Alert>
      )}

      {error && (
        <Alert tone="negative" title="Could not load policy configuration">
          {error} — the backend may still be starting.
        </Alert>
      )}

      <Split
        sidebar={
          <Card>
            {/* 对比模式切换 */}
            <div style={{ marginBottom: 'var(--ds-space-4)' }}>
              <Button
                variant={compareMode ? 'primary' : 'secondary'}
                size="sm"
                onClick={() => {
                  setCompareMode(!compareMode);
                  setCompareWithVersion(null);
                }}
              >
                {compareMode ? '✓ Compare mode ON' : 'Compare mode'}
              </Button>
            </div>

            {/* 对比模式界面 */}
            {compareMode ? (
              <>
                {/* 版本选择器 */}
                <div style={{ marginBottom: 'var(--ds-space-4)' }}>
                  <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600', fontSize: '14px' }}>
                    Base version:
                  </label>
                  <select
                    value={selectedVersion ?? ''}
                    onChange={(e) => setSelectedVersion(parseInt(e.target.value))}
                    style={{
                      width: '100%',
                      padding: '8px',
                      borderRadius: '4px',
                      border: '1px solid #d1d5db',
                      fontSize: '14px',
                    }}
                  >
                    <option value="">Select version...</option>
                    {versions.map((v) => (
                      <option key={v.version} value={v.version}>
                        v{v.version} {v.isCurrent ? '(current)' : '(superseded)'}
                      </option>
                    ))}
                  </select>
                </div>

                <div style={{ marginBottom: 'var(--ds-space-4)' }}>
                  <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600', fontSize: '14px' }}>
                    Compare with:
                  </label>
                  <select
                    value={compareWithVersion ?? ''}
                    onChange={(e) => setCompareWithVersion(parseInt(e.target.value))}
                    style={{
                      width: '100%',
                      padding: '8px',
                      borderRadius: '4px',
                      border: '1px solid #d1d5db',
                      fontSize: '14px',
                    }}
                  >
                    <option value="">Select version...</option>
                    {versions.map((v) => (
                      <option key={v.version} value={v.version}>
                        v{v.version} {v.isCurrent ? '(current)' : '(superseded)'}
                      </option>
                    ))}
                  </select>
                </div>

                {/* 对比摘要 */}
                {comparison && comparison.changes.length > 0 && (
                  <Card
                    style={{
                      marginBottom: 'var(--ds-space-4)',
                      backgroundColor: '#f0fdf4',
                      borderLeft: '4px solid #10b981',
                    }}
                  >
                    <div style={{ fontSize: '14px', marginBottom: '8px', fontWeight: '600' }}>
                      📊 {comparison.totalChanges} change{comparison.totalChanges !== 1 ? 's' : ''}
                    </div>
                    <div style={{ fontSize: '13px', lineHeight: '1.6' }}>
                      {comparison.changes.map((change, i) => (
                        <div key={i}>• {change}</div>
                      ))}
                    </div>
                  </Card>
                )}

                {comparison && comparison.changes.length === 0 && (
                  <Card style={{ backgroundColor: '#eff6ff', borderLeft: '4px solid #3b82f6' }}>
                    <div style={{ fontSize: '14px', color: '#1e40af' }}>✓ No differences found</div>
                  </Card>
                )}
              </>
            ) : (
              /* 普通模式：显示单个版本详情 */
              <>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: 'var(--ds-space-4)',
                  }}
                >
                  <div>
                    <div style={{ fontSize: '18px', fontWeight: '700' }}>
                      {selected ? `Version ${selected.version}` : 'No version selected'}
                    </div>
                    {selected && (
                      <div style={{ fontSize: '13px', color: '#6b7280', marginTop: '4px' }}>
                        {selected.isCurrent ? '🟢 Current' : '⚪ Superseded'}
                      </div>
                    )}
                  </div>
                  {selected?.isCurrent && <Badge tone="positive">CURRENT</Badge>}
                </div>

                {!selected ? (
                  <EmptyState title="Pick a version from the list" />
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
                    <div style={{ marginBottom: 'var(--ds-space-4)' }}>
                      <input
                        type="text"
                        placeholder="🔍 Search by name, reason, or DOB..."
                        value={restrictionSearchQuery}
                        onChange={(e) => setRestrictionSearchQuery(e.target.value)}
                        style={{
                          width: '100%',
                          padding: '8px 12px',
                          borderRadius: '4px',
                          border: '1px solid #d1d5db',
                          fontSize: '13px',
                          boxSizing: 'border-box',
                        }}
                      />
                      {restrictionSearchQuery && (
                        <div style={{ fontSize: '12px', color: '#6b7280', marginTop: '4px' }}>
                          Found {filterRestrictions(selected.restrictionList ?? []).length} of {(selected.restrictionList ?? []).length}
                        </div>
                      )}
                    </div>
                    {(selected.restrictionList ?? []).length === 0 ? (
                      <EmptyState title="No restricted entries" />
                    ) : filterRestrictions(selected.restrictionList ?? []).length === 0 ? (
                      <EmptyState title="No results match your search" />
                    ) : (
                      filterRestrictions(selected.restrictionList ?? []).map((entry, i) => (
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
              </>
            )}
          </Card>
        }
      >
        {/* 对比结果显示 */}
        {compareMode && selected && compareWith ? (
          <Stack gap={6}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--ds-space-6)' }}>
              {/* 左侧：基准版本 */}
              <Card title={`Version ${selected.version}`} subtitle="Base version">
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
                      value: renderArrayWithDiff(
                        selected.supportedResidencies,
                        comparison?.supportedDiff
                      ),
                    },
                    {
                      label: 'Excluded residencies',
                      value: renderArrayWithDiff(
                        selected.excludedResidencies,
                        comparison?.excludedDiff
                      ),
                    },
                    { label: 'Sample every (X)', value: String(selected.sampleEvery) },
                  ]}
                />
              </Card>

              {/* 右侧：对比版本 */}
              <Card title={`Version ${compareWith.version}`} subtitle="Comparison version">
                <KeyValue
                  items={[
                    {
                      label: 'Effective from',
                      value: compareWith.effectiveFrom
                        ? new Date(compareWith.effectiveFrom).toLocaleString()
                        : '—',
                    },
                    {
                      label: 'Supported residencies',
                      value: renderArrayWithDiff(
                        compareWith.supportedResidencies,
                        comparison?.supportedDiff
                          ? {
                              added: comparison.supportedDiff.removed || [],
                              removed: comparison.supportedDiff.added || [],
                            }
                          : 'same'
                      ),
                    },
                    {
                      label: 'Excluded residencies',
                      value: renderArrayWithDiff(
                        compareWith.excludedResidencies,
                        comparison?.excludedDiff
                          ? {
                              added: comparison.excludedDiff.removed || [],
                              removed: comparison.excludedDiff.added || [],
                            }
                          : 'same'
                      ),
                    },
                    { label: 'Sample every (X)', value: String(compareWith.sampleEvery) },
                  ]}
                />
              </Card>
            </div>

            {/* 限制清单对比 */}
            <Card title="Restriction List Comparison">
              <div style={{ marginBottom: 'var(--ds-space-4)' }}>
                <input
                  type="text"
                  placeholder="🔍 Search by name, reason, or DOB..."
                  value={restrictionSearchQuery}
                  onChange={(e) => setRestrictionSearchQuery(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    borderRadius: '4px',
                    border: '1px solid #d1d5db',
                    fontSize: '13px',
                    boxSizing: 'border-box',
                  }}
                />
                {restrictionSearchQuery && (
                  <div style={{ fontSize: '12px', color: '#6b7280', marginTop: '4px' }}>
                    Filtered results shown below
                  </div>
                )}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--ds-space-6)' }}>
                <div>
                  <h4 style={{ marginBottom: 'var(--ds-space-3)', fontWeight: '600' }}>
                    v{selected.version} ({filterRestrictions(selected.restrictionList ?? []).length} of {selected.restrictionList?.length ?? 0})
                  </h4>
                  {(selected.restrictionList ?? []).length === 0 ? (
                    <div style={{ color: '#9ca3af', fontSize: '14px' }}>No entries</div>
                  ) : filterRestrictions(selected.restrictionList ?? []).length === 0 ? (
                    <div style={{ color: '#9ca3af', fontSize: '14px' }}>No results match</div>
                  ) : (
                    filterRestrictions(selected.restrictionList ?? []).map((entry, i) => (
                      <Card key={i} style={{ marginBottom: 'var(--ds-space-3)', backgroundColor: '#f9fafb' }}>
                        <div style={{ fontSize: '13px', lineHeight: '1.6' }}>
                          <div style={{ fontWeight: '600' }}>{entry.fullName}</div>
                          <div style={{ color: '#6b7280' }}>{entry.dateOfBirth}</div>
                          <div style={{ color: '#6b7280', fontSize: '12px' }}>{entry.reason}</div>
                        </div>
                      </Card>
                    ))
                  )}
                </div>

                <div>
                  <h4 style={{ marginBottom: 'var(--ds-space-3)', fontWeight: '600' }}>
                    v{compareWith.version} ({filterRestrictions(compareWith.restrictionList ?? []).length} of {compareWith.restrictionList?.length ?? 0})
                  </h4>
                  {(compareWith.restrictionList ?? []).length === 0 ? (
                    <div style={{ color: '#9ca3af', fontSize: '14px' }}>No entries</div>
                  ) : filterRestrictions(compareWith.restrictionList ?? []).length === 0 ? (
                    <div style={{ color: '#9ca3af', fontSize: '14px' }}>No results match</div>
                  ) : (
                    filterRestrictions(compareWith.restrictionList ?? []).map((entry, i) => (
                      <Card key={i} style={{ marginBottom: 'var(--ds-space-3)', backgroundColor: '#f9fafb' }}>
                        <div style={{ fontSize: '13px', lineHeight: '1.6' }}>
                          <div style={{ fontWeight: '600' }}>{entry.fullName}</div>
                          <div style={{ color: '#6b7280' }}>{entry.dateOfBirth}</div>
                          <div style={{ color: '#6b7280', fontSize: '12px' }}>{entry.reason}</div>
                        </div>
                      </Card>
                    ))
                  )}
                </div>
              </div>
            </Card>
          </Stack>
        ) : (
          /* 普通模式：版本列表 */
          <DataTable
            columns={columns}
            rows={versions}
            rowKey={(v) => v.version}
            onRowClick={(v) => {
              setSelectedVersion(v.version);
              setCompareWithVersion(null);
            }}
            selectedKey={selectedVersion}
            footnote="oldest first"
            empty={<EmptyState title="No versions found" />}
          />
        )}
      </Split>

      {editorOpen && current && (
        <PolicyConfigEditor
          key={current.version}
          open
          current={current}
          onClose={() => setEditorOpen(false)}
          onPublished={(version) => {
            setEditorOpen(false);
            setPublishedVersion(version);
            loadVersions(version);
          }}
        />
      )}
    </>
  );
}
