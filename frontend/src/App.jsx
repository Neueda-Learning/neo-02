import React, { useCallback, useEffect, useRef, useState } from 'react';
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
  const [applicationPage, setApplicationPage] = useState(0);
  const [applicationFilter, setApplicationFilter] = useState('All');
  const [applicationMore, setApplicationMore] = useState(false);
  const [applicationTotal, setApplicationTotal] = useState(0);
  const [applicationAllTotal, setApplicationAllTotal] = useState(0);
  const [applicationCounts, setApplicationCounts] = useState({});
  const [error, setError] = useState(null);
  const [health, setHealth] = useState(null);
  const [info, setInfo] = useState(null);
  const applicationRequestVersion = useRef(0);

  const reload = useCallback(async () => {
    const requestVersion = ++applicationRequestVersion.current;
    try {
      const result = await api.listApplications(applicationPage, applicationFilter);
      if (requestVersion !== applicationRequestVersion.current) return;
      setRequests(result.results);
      setApplicationMore(result.more);
      setApplicationTotal(result.total);
      setApplicationAllTotal(result.allTotal);
      setApplicationCounts(result.counts);
      setError(null);
    } catch (e) {
      if (requestVersion !== applicationRequestVersion.current) return;
      setError(e.message);
    }
  }, [applicationFilter, applicationPage]);

  useEffect(() => {
    reload();
    const id = setInterval(reload, POLL_MS);
    return () => {
      clearInterval(id);
      applicationRequestVersion.current += 1;
    };
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
          page={applicationPage}
          more={applicationMore}
          total={applicationTotal}
          allTotal={applicationAllTotal}
          counts={applicationCounts}
          filter={applicationFilter}
          onPageChange={setApplicationPage}
          onFilterChange={(nextFilter) => {
            setApplicationPage(0);
            setApplicationFilter(nextFilter);
          }}
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
