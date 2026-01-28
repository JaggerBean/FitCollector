import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuthContext } from "../app/AuthContext";

export function Layout({ title, children }: { title?: string; children: ReactNode }) {
  const { isAuthenticated, logout } = useAuthContext();

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 to-slate-900">
      <header className="border-b border-slate-900/20 bg-slate-950/70 backdrop-blur">
        <div className="mx-auto flex h-16 w-full max-w-[1280px] items-center justify-between px-6 md:h-[72px] md:px-10">
        <div>
          <Link to="/" className="flex items-center gap-3">
            <img src="/logo.png" alt="StepCraft" className="h-10 w-auto" />
            <div className="text-xl font-semibold text-emerald-700 dark:text-emerald-200">StepCraft</div>
          </Link>
          {title ? <div className="text-sm text-slate-500 dark:text-slate-400">{title}</div> : null}
        </div>
        {isAuthenticated ? (
          <button
            onClick={logout}
            className="rounded-full border border-emerald-200 bg-white px-4 py-2 text-sm font-medium text-emerald-700 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 dark:border-slate-700 dark:bg-slate-900 dark:text-emerald-200"
          >
            Sign out
          </button>
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
      <main className="mx-auto w-full max-w-[1280px] px-6 pb-16 md:px-10">{children}</main>
    </div>
  );
}
