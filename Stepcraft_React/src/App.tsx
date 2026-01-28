import { Navigate, Route, Routes } from "react-router-dom";
import { useAuthContext } from "./app/AuthContext";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import RegisterServerPage from "./pages/RegisterServerPage";
import ServerManagePage from "./pages/ServerManagePage";
import RewardsPage from "./pages/RewardsPage";
import PushPage from "./pages/PushPage";
import NotFoundPage from "./pages/NotFoundPage";

function RequireAuth({ children }: { children: JSX.Element }) {
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
      <Route path="/" element={<Navigate to={isAuthenticated ? "/dashboard" : "/login"} replace />} />
      <Route path="/login" element={isAuthenticated ? <Navigate to="/dashboard" replace /> : <LoginPage />} />
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
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
