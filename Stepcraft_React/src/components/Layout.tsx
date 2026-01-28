import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuthContext } from "../app/AuthContext";

export function Layout({ title, children }: { title: string; children: ReactNode }) {
  const { isAuthenticated, logout } = useAuthContext();

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-emerald-50 dark:from-slate-950 dark:to-slate-900">
      <header className="mx-auto flex w-full max-w-7xl items-center justify-between px-6 py-6">
        <div>
          <Link to="/" className="flex items-center gap-3">
            <img src="/logo.png" alt="StepCraft" className="h-10 w-auto" />
            <div className="text-xl font-semibold text-emerald-700 dark:text-emerald-200">StepCraft</div>
          </Link>
          <div className="text-sm text-slate-500 dark:text-slate-400">{title}</div>
        </div>
        {isAuthenticated ? (
          <button
            onClick={logout}
            className="rounded-full border border-emerald-200 bg-white px-4 py-2 text-sm font-medium text-emerald-700 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 dark:border-slate-700 dark:bg-slate-900 dark:text-emerald-200"
          >
            Sign out
          </button>
        ) : (
          <div className="flex items-center gap-3">
            <Link
              to="/login"
              className="text-sm font-medium text-slate-600 hover:text-emerald-600 dark:text-slate-300"
            >
              Sign in
            </Link>
            <Link
              to="/account/register"
              className="rounded-full border border-emerald-200 bg-white px-4 py-2 text-sm font-medium text-emerald-700 shadow-sm transition hover:border-emerald-300 hover:bg-emerald-50 dark:border-slate-700 dark:bg-slate-900 dark:text-emerald-200"
            >
              Create account
            </Link>
          </div>
        )}
      </header>
      <main className="mx-auto w-full max-w-7xl px-6 pb-12">{children}</main>
    </div>
  );
}
