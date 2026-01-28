import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuthContext } from "../app/AuthContext";

export function Layout({ children }: { title?: string; children: ReactNode }) {
  const { isAuthenticated, logout } = useAuthContext();

  return (
    <div className="flex min-h-screen flex-col app-shell">
      <header className="app-header border-b border-default backdrop-blur">
        <div className="mx-auto flex h-16 w-full max-w-[1280px] items-center justify-between px-6 md:h-[72px] md:px-10">
        <div>
          <Link to="/" className="flex items-center gap-3">
            <img src="/logo.png" alt="StepCraft" className="h-10 w-auto" />
            <div className="text-xl font-semibold text-accent">StepCraft</div>
          </Link>
        </div>
        {isAuthenticated ? (
          <button
            onClick={logout}
            className="btn-secondary rounded-full px-4 py-2 text-sm font-medium shadow-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--accent-1)]"
          >
            Sign out
          </button>
        ) : (
          <div className="flex items-center gap-3">
            <Link
              to="/login"
              className="text-sm font-medium text-muted hover:text-[color:var(--accent-1)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--accent-1)]"
            >
              Sign in
            </Link>
            <Link
              to="/account/register"
              className="btn-primary rounded-full px-4 py-2 text-sm font-semibold shadow-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--accent-1)]"
            >
              Create account
            </Link>
          </div>
        )}
        </div>
      </header>
      <main className="mx-auto w-full max-w-[1280px] flex-1 px-6 pb-16 md:px-10">{children}</main>
      <footer className="app-footer mt-10 border-t border-default">
        <div className="mx-auto flex w-full max-w-[1280px] items-center justify-between px-6 py-6 text-xs text-muted md:px-10">
          <div>© {new Date().getFullYear()} StepCraft</div>
          <div className="flex items-center gap-3">
            <a
              href="#"
              aria-label="StepCraft Minecraft mod page (coming soon)"
              className="flex h-9 w-9 items-center justify-center rounded-full border border-default text-muted transition hover:border-[color:var(--accent-1)] hover:text-[color:var(--accent-1)]"
            >
              <svg viewBox="0 0 24 24" className="h-4 w-4" aria-hidden="true" fill="currentColor">
                <path d="M12 2 2.5 7v10L12 22l9.5-5V7L12 2zm0 2.2 7.3 3.8L12 12 4.7 8l7.3-3.8zm-7 5.9L11 13.9v5.9l-6-3.2v-6.5zm14 0v6.5l-6 3.2v-5.9l6-3.8z" />
              </svg>
            </a>
            <a
              href="#"
              aria-label="StepCraft phone app (coming soon)"
              className="flex h-9 w-9 items-center justify-center rounded-full border border-default text-muted transition hover:border-[color:var(--accent-1)] hover:text-[color:var(--accent-1)]"
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
