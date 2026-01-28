import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";

export default function LandingPage() {
  const { isAuthenticated } = useAuthContext();

  return (
    <Layout title="Welcome">
      <div className="grid gap-10 lg:grid-cols-[1.1fr_0.9fr]">
        <div className="rounded-3xl border border-emerald-100 bg-white p-8 shadow-xl dark:border-slate-800 dark:bg-slate-900">
          <h1 className="text-3xl font-semibold text-slate-900 dark:text-slate-100">StepCraft Dashboard</h1>
          <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
            Manage your Minecraft fitness servers, invite codes, rewards, and push notifications in one place.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            {isAuthenticated ? (
              <Link
                to="/dashboard"
                className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-emerald-700"
              >
                Go to dashboard
              </Link>
            ) : (
              <>
                <Link
                  to="/login"
                  className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-emerald-700"
                >
                  Sign in
                </Link>
                <Link
                  to="/account/register"
                  className="rounded-lg border border-emerald-200 bg-white px-4 py-2 text-sm font-semibold text-emerald-700 shadow-sm hover:border-emerald-300 hover:bg-emerald-50 dark:border-slate-700 dark:bg-slate-900 dark:text-emerald-200"
                >
                  Create account
                </Link>
              </>
            )}
          </div>
        </div>
        <div className="rounded-3xl border border-slate-200 bg-white p-8 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">What you can do</h2>
          <ul className="mt-4 space-y-3 text-sm text-slate-600 dark:text-slate-300">
            <li>• Register servers and manage invite codes</li>
            <li>• Edit reward tiers and default rewards</li>
            <li>• Schedule push notifications</li>
            <li>• Ban, unban, and wipe players</li>
            <li>• Check claim status and steps</li>
          </ul>
        </div>
      </div>
    </Layout>
  );
}
