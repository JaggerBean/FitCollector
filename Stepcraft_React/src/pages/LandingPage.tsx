import { Link, useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { Layout } from "../components/Layout";
import MagicBento from "../components/MagicBento";
import { useAuthContext } from "../app/AuthContext";

export default function LandingPage() {
  const { isAuthenticated } = useAuthContext();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) {
      navigate("/dashboard", { replace: true });
    }
  }, [isAuthenticated, navigate]);

  return (
    <Layout>
      <div className="space-y-12 md:space-y-16">
        <section className="relative w-screen left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] bg-slate-950">
          <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.12),transparent_55%),linear-gradient(to_bottom,rgba(2,6,23,0.7),rgba(2,6,23,0.95))]" />
          <div className="relative mx-auto w-full max-w-[1280px] px-6 pt-24 pb-20 md:px-10">
            <div className="grid gap-12 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
              <div className="max-w-xl">
                <h1 className="text-4xl font-bold leading-[1.08] text-white md:text-6xl">
                  Inspire daily movement with a rewards-driven Minecraft server
                </h1>
                <p className="mt-6 text-base leading-relaxed text-slate-300 md:text-lg">
                  StepCraft turns step goals into in-game rewards. Launch a private or public server, automate rewards,
                  and keep your community engaged every single day.
                </p>
                <p className="mt-3 text-xs uppercase tracking-[0.14em] text-emerald-300">
                  Rewards delivered daily • Steps tracked across servers
                </p>
                <div className="mt-8 flex flex-wrap gap-4">
                  {isAuthenticated ? (
                    <Link
                      to="/dashboard"
                      className="flex h-11 items-center justify-center rounded-md bg-emerald-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                    >
                      Go to dashboard
                    </Link>
                  ) : (
                    <>
                      <Link
                        to="/account/register"
                        className="flex h-11 items-center justify-center rounded-md bg-emerald-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                      >
                        Create your account
                      </Link>
                      <Link
                        to="/login"
                        className="flex h-11 items-center justify-center rounded-md border border-slate-700 px-6 text-sm font-semibold text-white transition hover:border-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                      >
                        Sign in
                      </Link>
                    </>
                  )}
                </div>
                <div className="mt-3 text-xs text-slate-400">Takes 30 seconds. No credit card.</div>
              </div>
              <div className="rounded-2xl border border-slate-800 bg-slate-950/60 p-6 shadow-[0_8px_30px_rgba(15,23,42,0.35)]">
                <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Today’s rewards</div>
                <div className="mt-4 space-y-3">
                  {["1,000 steps → Starter kit", "5,000 steps → Iron bundle", "10,000 steps → Legendary drop"].map(
                    (line) => (
                      <div
                        key={line}
                        className="rounded-lg border border-slate-800 bg-slate-950 px-4 py-3 text-sm text-slate-200"
                      >
                        {line}
                      </div>
                    ),
                  )}
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="space-y-6">
          <div className="text-xs uppercase tracking-[0.16em] text-slate-400">How it works</div>
          <MagicBento
            glowColor="45, 212, 191"
            enableTilt
            cards={[
              {
                color: "#060010",
                title: "Launch in 3 steps",
                description:
                  "01  Register your server — Get an API key and invite code instantly.\n" +
                  "02  Set rewards — Configure commands per step tier.\n" +
                  "03  Go live — Invite players and watch streaks grow.",
                label: "Launch sequence",
                className: "magic-bento-card--launch",
              },
            ]}
          />
        </section>

        <section className="space-y-6">
          <div className="text-xs uppercase tracking-[0.16em] text-slate-400">Features</div>
          <MagicBento
            glowColor="45, 212, 191"
            enableTilt
            cards={[
              {
                color: "#060010",
                title: "Control who joins your server",
                description: "Invite codes keep your private community secure.",
                label: "Access",
              },
              {
                color: "#060010",
                title: "Automate rewards for step streaks",
                description: "Reward tiers run commands the moment goals are hit.",
                label: "Automation",
              },
              {
                color: "#060010",
                title: "Bring players back daily",
                description: "Push alerts keep streaks alive and servers active.",
                label: "Engagement",
              },
            ]}
          />
        </section>

        <section className="space-y-6">
          <div className="text-xs uppercase tracking-[0.16em] text-slate-400">Trust & community</div>
          <MagicBento
            glowColor="45, 212, 191"
            enableTilt
            cards={[
              {
                color: "#060010",
                title: "Why communities love it",
                description:
                  "• Automated rewards and claim tracking\n" +
                  "• Real-time player tools and ban controls\n" +
                  "• Works with private invite-only servers",
                label: "Outcomes",
              },
              {
                color: "#060010",
                title: "Community admin",
                description:
                  "“We doubled daily activity in two weeks. Players log in just to hit the next step tier.”",
                label: "Community",
              },
            ]}
          />
        </section>

        <section className="rounded-2xl border border-slate-800 bg-slate-950/70 p-8 text-center">
          <h2 className="text-2xl font-semibold text-white">Ready to build your StepCraft community?</h2>
          <p className="mt-2 text-sm text-slate-400">Create your account and launch in minutes.</p>
          <div className="mt-6 flex justify-center">
            <Link
              to={isAuthenticated ? "/dashboard" : "/account/register"}
              className="flex h-11 items-center justify-center rounded-md bg-emerald-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
            >
              {isAuthenticated ? "Go to dashboard" : "Create your account"}
            </Link>
          </div>
        </section>
      </div>
    </Layout>
  );
}
