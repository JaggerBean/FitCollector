import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";

export default function LandingPage() {
  const { isAuthenticated } = useAuthContext();

  return (
    <Layout title="Welcome">
      <div className="grid gap-10 lg:grid-cols-[1.2fr_0.8fr]">
        <div className="space-y-8">
          <div className="rounded-3xl border border-emerald-100 bg-white p-8 shadow-xl dark:border-slate-800 dark:bg-slate-900">
            <div className="inline-flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
              Fitness meets Minecraft
            </div>
            <h1 className="mt-4 text-4xl font-semibold text-slate-900 dark:text-slate-100">
              Inspire daily movement with a rewards-driven Minecraft server
            </h1>
            <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">
              StepCraft turns step goals into in-game rewards. Launch a private or public server, automate rewards,
              and keep your community engaged every single day.
            </p>
            <div className="mt-6 flex flex-wrap gap-3">
              {isAuthenticated ? (
                <Link
                  to="/dashboard"
                  className="rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white shadow hover:bg-emerald-700"
                >
                  Go to dashboard
                </Link>
              ) : (
                <>
                  <Link
                    to="/account/register"
                    className="rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white shadow hover:bg-emerald-700"
                  >
                    Create your account
                  </Link>
                  <Link
                    to="/login"
                    className="rounded-lg border border-emerald-200 bg-white px-5 py-2.5 text-sm font-semibold text-emerald-700 shadow-sm hover:border-emerald-300 hover:bg-emerald-50 dark:border-slate-700 dark:bg-slate-900 dark:text-emerald-200"
                  >
                    Sign in
                  </Link>
                </>
              )}
            </div>
            <div className="mt-6 grid gap-4 sm:grid-cols-3">
              <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-300">
                <div className="text-lg font-semibold text-slate-900 dark:text-slate-100">Invite codes</div>
                Private servers with controlled access.
              </div>
              <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-300">
                <div className="text-lg font-semibold text-slate-900 dark:text-slate-100">Reward tiers</div>
                Customize goals and commands for each tier.
              </div>
              <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-300">
                <div className="text-lg font-semibold text-slate-900 dark:text-slate-100">Push alerts</div>
                Keep players informed with scheduled updates.
              </div>
            </div>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
              <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Why communities love it</h2>
              <ul className="mt-4 space-y-3 text-sm text-slate-600 dark:text-slate-300">
                <li>• Automated rewards and claim tracking</li>
                <li>• Real-time player tools and ban controls</li>
                <li>• Works with private invite-only servers</li>
              </ul>
            </div>
            <div className="rounded-3xl border border-emerald-100 bg-emerald-50 p-6 text-sm text-emerald-700 shadow-sm dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
              “We doubled daily activity in two weeks. Players log in just to hit the next step tier.”
              <div className="mt-4 text-xs uppercase tracking-wide text-emerald-700/70">Community Admin</div>
            </div>
          </div>
        </div>
        <div className="space-y-6">
          <div className="rounded-3xl border border-slate-200 bg-white p-8 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Launch in 3 steps</h2>
            <ol className="mt-4 space-y-4 text-sm text-slate-600 dark:text-slate-300">
              <li>
                <span className="font-semibold text-slate-900 dark:text-slate-100">1. Register your server</span>
                <div>Get an API key and invite code instantly.</div>
              </li>
              <li>
                <span className="font-semibold text-slate-900 dark:text-slate-100">2. Set rewards</span>
                <div>Configure commands per step tier.</div>
              </li>
              <li>
                <span className="font-semibold text-slate-900 dark:text-slate-100">3. Go live</span>
                <div>Invite players and watch streaks grow.</div>
              </li>
            </ol>
            <div className="mt-6 rounded-2xl border border-emerald-100 bg-emerald-50 p-4 text-xs text-emerald-700 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
              Built for admins who want motivation, not manual work.
            </div>
          </div>
          <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Ready to enroll?</h2>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
              Join the owners turning daily steps into daily playtime.
            </p>
            <div className="mt-4 flex flex-wrap gap-3">
              <Link
                to={isAuthenticated ? "/dashboard" : "/account/register"}
                className="rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white shadow hover:bg-emerald-700"
              >
                {isAuthenticated ? "Go to dashboard" : "Create account"}
              </Link>
              <Link
                to="/register"
                className="rounded-lg border border-emerald-200 bg-white px-5 py-2.5 text-sm font-semibold text-emerald-700 shadow-sm hover:border-emerald-300 hover:bg-emerald-50 dark:border-slate-700 dark:bg-slate-900 dark:text-emerald-200"
              >
                Register a server
              </Link>
            </div>
          </div>
        </div>
      </div>
    </Layout>
  );
}
