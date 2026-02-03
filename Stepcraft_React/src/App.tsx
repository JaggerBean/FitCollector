import type { ReactElement } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuthContext } from "./app/AuthContext";
import LandingPage from "./pages/LandingPage";
import LoginPage from "./pages/LoginPage";
import RegisterAccountPage from "./pages/RegisterAccountPage";
import DashboardPage from "./pages/DashboardPage";
import RegisterServerPage from "./pages/RegisterServerPage";
import ServerManagePage from "./pages/ServerManagePage";
import RewardsPage from "./pages/RewardsPage";
import PushPage from "./pages/PushPage";
import NotFoundPage from "./pages/NotFoundPage";
import AuditLogPage from "./pages/AuditLogPage";

function RequireAuth({ children }: { children: ReactElement }) {
  const { isAuthenticated } = useAuthContext();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function App() {
  const { isAuthenticated } = useAuthContext();

  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginPage />} />
      <Route
        path="/account/register"
        element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <RegisterAccountPage />}
      />
      <Route
        path="/dashboard"
        element={
          <RequireAuth>
            <DashboardPage />
          </RequireAuth>
        }
      />
      <Route
        path="/register"
        element={
          <RequireAuth>
            <RegisterServerPage />
          </RequireAuth>
        }
      />
      <Route
        path="/servers/:serverName"
        element={
          <RequireAuth>
            <ServerManagePage />
          </RequireAuth>
        }
      />
      <Route
        path="/servers/:serverName/rewards"
        element={
          <RequireAuth>
            <RewardsPage />
          </RequireAuth>
        }
      />
      <Route
        path="/servers/:serverName/push"
        element={
          <RequireAuth>
            <PushPage />
          </RequireAuth>
        }
      />
      <Route
        path="/audit"
        element={
          <RequireAuth>
            <AuditLogPage />
          </RequireAuth>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
