import React, { useCallback, useEffect, useState } from 'react';
import { AppShell, Button, SideBrand, SideNav, StatusPill } from './design-system';
import DecisionDetailScreen from './components/DecisionDetailScreen.jsx';
import RequestsScreen from './components/RequestsScreen.jsx';
import { api } from './api.js';

const POLL_MS = 2000;
const HEALTH_MS = 10000;

export default function App() {
  const [screen, setScreen] = useState('applications');
  const [selectedCaseId, setSelectedCaseId] = useState(null);
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState(null);
  const [health, setHealth] = useState(null);
  const [info, setInfo] = useState(null);

  const reload = useCallback(async () => {
    try {
      setRequests(await api.listApplications());
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    reload();
    const id = setInterval(reload, POLL_MS);
    return () => clearInterval(id);
  }, [reload]);

  const refreshHealth = useCallback(async () => {
    try {
      const [nextHealth, nextInfo] = await Promise.all([api.health(), api.info()]);
      setHealth(nextHealth);
      setInfo(nextInfo);
    } catch {
      setHealth(null);
    }
  }, []);

  useEffect(() => {
    refreshHealth();
    const id = setInterval(refreshHealth, HEALTH_MS);
    return () => clearInterval(id);
  }, [refreshHealth]);

  const up = !error && health?.status === 'UP';
  const screens = [
    { id: 'applications', label: 'Applications' },
    {
      id: 'decision',
      label: 'Decision detail',
      hint: selectedCaseId ?? 'select an application',
      disabled: !selectedCaseId,
    },
    { id: 'overrides', label: 'Overrides', hint: 'operator actions', disabled: true },
    { id: 'settings', label: 'Settings', hint: 'reference data', disabled: true },
  ];

  const openCase = (row) => {
    setSelectedCaseId(row.applicationId);
    setScreen('decision');
  };

  return (
    <AppShell
      side={
        <>
          <SideBrand
            brand={info?.team ?? 'Team'}
            product={info?.service ?? 'Module'}
            meta={info ? `${info.serviceId} | ${info.domain}` : undefined}
          />
          <SideNav items={screens} active={screen} onSelect={setScreen} />
          <div className="app-side-status">
            <StatusPill tone={up ? 'positive' : 'negative'}>{up ? 'Up' : 'Down'}</StatusPill>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                reload();
                refreshHealth();
              }}
            >
              Refresh
            </Button>
          </div>
        </>
      }
      footer="Customer Policy | applications arrive from the orchestrator"
    >
      {screen === 'applications' && (
        <RequestsScreen requests={requests} error={error} info={info} onOpenCase={openCase} />
      )}
      {screen === 'decision' && selectedCaseId && (
        <DecisionDetailScreen
          applicationId={selectedCaseId}
          onBack={() => setScreen('applications')}
        />
      )}
    </AppShell>
  );
}
