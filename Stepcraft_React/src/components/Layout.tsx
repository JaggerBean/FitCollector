import { Link, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuthContext } from "../app/AuthContext";

export function Layout({ children }: { children: ReactNode }) {
  const { isAuthenticated, logout } = useAuthContext();
  const location = useLocation();
  const showDashboard =
    isAuthenticated && location.pathname !== "/dashboard" && location.pathname !== "/";
  const serverMatch = location.pathname.match(/^\/servers\/([^/]+)/);
  const currentServer = serverMatch ? decodeURIComponent(serverMatch[1]) : null;
  const showAudit = Boolean(isAuthenticated && currentServer);

  return (
    <div className="flex min-h-screen flex-col bg-gradient-to-br from-slate-950 to-slate-900">
      <header className="border-b border-slate-900/20 bg-slate-950/70 backdrop-blur">
        <div className="mx-auto flex h-16 w-full max-w-[1280px] items-center justify-between px-6 md:h-[72px] md:px-10">
        <div>
          <Link to="/" className="flex items-center gap-3">
            <img src="/logo.png" alt="StepCraft" className="h-10 w-auto" />
            <div className="text-xl font-semibold text-emerald-700 dark:text-emerald-200">StepCraft</div>
          </Link>
        </div>
        {isAuthenticated ? (
          <div className="flex items-center gap-3">
            {showDashboard && (
              <Link
                to="/dashboard"
                className="rounded-full border border-slate-600 bg-slate-900/70 px-4 py-2 text-sm font-medium text-slate-100 shadow-sm transition hover:border-emerald-500/70 hover:bg-emerald-600 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
              >
                Dashboard
              </Link>
            )}
            {showAudit && (
              <Link
                to={`/servers/${encodeURIComponent(currentServer as string)}#audit`}
                className="rounded-full border border-slate-600 bg-slate-900/70 px-4 py-2 text-sm font-medium text-slate-100 shadow-sm transition hover:border-emerald-500/70 hover:bg-emerald-600 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
              >
                Audit log
              </Link>
            )}
            <button
              onClick={logout}
              className="rounded-full border border-emerald-200 bg-white px-4 py-2 text-sm font-medium text-emerald-700 shadow-sm transition hover:border-emerald-600 hover:bg-emerald-600 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 dark:border-slate-700 dark:bg-slate-900 dark:text-emerald-200 dark:hover:border-emerald-500 dark:hover:bg-emerald-600"
            >
              Sign out
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-3">
            <Link
              to="/login"
              className="text-sm font-medium text-slate-300 hover:text-emerald-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
            >
              Sign in
            </Link>
            <Link
              to="/account/register"
              className="rounded-full bg-emerald-500 px-4 py-2 text-sm font-semibold text-slate-950 shadow-sm transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
            >
              Create account
            </Link>
          </div>
        )}
        </div>
      </header>
      <main className="mx-auto w-full max-w-[1280px] flex-1 px-6 pb-16 md:px-10">{children}</main>
      <footer className="mt-10 border-t border-slate-800/70 bg-slate-950/80">
        <div className="mx-auto flex w-full max-w-[1280px] items-center justify-between px-6 py-6 text-xs text-slate-500 md:px-10">
          <div>© {new Date().getFullYear()} StepCraft</div>
          <div className="flex items-center gap-3">
            <a
              href="#"
              aria-label="StepCraft Minecraft mod page (coming soon)"
              className="flex h-9 w-9 items-center justify-center rounded-full border border-slate-800 text-slate-300 transition hover:border-emerald-400/60 hover:text-emerald-200"
            >
              <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true" fill="currentColor">
                <path d="M12 2 2.5 7v10L12 22l9.5-5V7L12 2zm0 2.2 7.3 3.8L12 12 4.7 8l7.3-3.8zm-7 5.9L11 13.9v5.9l-6-3.2v-6.5zm14 0v6.5l-6 3.2v-5.9l6-3.8z" />
              </svg>
            </a>
            <a
              href="#"
              aria-label="StepCraft phone app (coming soon)"
              className="flex h-9 w-9 items-center justify-center rounded-full border border-slate-800 text-slate-300 transition hover:border-emerald-400/60 hover:text-emerald-200"
            >
              <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true" fill="currentColor">
                <path d="M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zm0 2v16h8V4H8zm4 13.5a1.25 1.25 0 1 0 0 2.5 1.25 1.25 0 0 0 0-2.5z" />
              </svg>
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
