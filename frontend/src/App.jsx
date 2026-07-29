import React, { useCallback, useEffect, useState } from 'react';
import { AppShell, Button, SideBrand, SideNav, StatusPill } from './design-system';
import CasesScreen from './components/CasesScreen.jsx';
import DecisionDetailScreen from './components/DecisionDetailScreen.jsx';
import PolicyConfigScreen from './components/PolicyConfigScreen.jsx';
import RejectionPatternsScreen from './components/RejectionPatternsScreen.jsx';
import ReferralQueueScreen from './components/ReferralQueueScreen.jsx';
import RequestsScreen from './components/RequestsScreen.jsx';
import { api } from './api.js';

const POLL_MS = 2000;
const HEALTH_MS = 10000;

export default function App() {
  const [screen, setScreen] = useState('applications');
  const [selectedCaseId, setSelectedCaseId] = useState(null);
  const [detailReturnScreen, setDetailReturnScreen] = useState('applications');
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
    { id: 'patterns', label: 'Rejection Patterns' },
    { id: 'referrals', label: 'Referral Queue', hint: 'human review' },
    { id: 'cases', label: 'Search cases' },
    { id: 'overrides', label: 'Overrides', hint: 'operator actions', disabled: true },
    { id: 'settings', label: 'Policy Config' },
  ];

  const openCase = (row, returnScreen) => {
    setSelectedCaseId(row.applicationId);
    setDetailReturnScreen(returnScreen);
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
          <SideNav
            items={screens}
            active={screen === 'decision' ? detailReturnScreen : screen}
            onSelect={setScreen}
          />
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
        <RequestsScreen
          requests={requests}
          error={error}
          info={info}
          onOpenCase={(row) => openCase(row, 'applications')}
        />
      )}
      {screen === 'patterns' && <RejectionPatternsScreen />}
      {screen === 'referrals' && (
        <ReferralQueueScreen onOpenCase={(row) => openCase(row, 'referrals')} />
      )}
      {screen === 'cases' && (
        <CasesScreen info={info} onOpenCase={(row) => openCase(row, 'cases')} />
      )}
      {screen === 'decision' && selectedCaseId && (
        <DecisionDetailScreen
          applicationId={selectedCaseId}
          backLabel={
            detailReturnScreen === 'referrals'
              ? 'Back to referral queue'
              : detailReturnScreen === 'cases'
                ? 'Back to search cases'
                : 'Back to applications'
          }
          onBack={() => setScreen(detailReturnScreen)}
        />
      )}
      {screen === 'settings' && <PolicyConfigScreen />}
    </AppShell>
  );
}
