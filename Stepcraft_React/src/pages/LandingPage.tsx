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
    <Layout title="Welcome">
      <div className="space-y-20">
        <section className="relative w-screen left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] bg-slate-950 py-24">
          <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-slate-900/80 via-slate-950 to-slate-950" />
          <div className="pointer-events-none absolute -left-24 top-12 h-64 w-64 rounded-full bg-emerald-400/10 blur-3xl" />
          <div className="pointer-events-none absolute right-0 top-24 h-80 w-80 rounded-full bg-blue-400/10 blur-3xl" />
          <div className="relative mx-auto w-full max-w-screen-2xl px-6 2xl:px-10">
            <div className="max-w-3xl">
              <h1 className="text-5xl font-semibold text-white md:text-6xl">
                Inspire daily movement with a rewards-driven Minecraft server
              </h1>
              <p className="mt-6 text-base text-slate-300 md:text-lg">
                StepCraft turns step goals into in-game rewards. Launch a private or public server, automate rewards,
                and keep your community engaged every single day.
              </p>
              <div className="mt-8 flex flex-wrap gap-4">
                {isAuthenticated ? (
                  <Link
                    to="/dashboard"
                    className="rounded-md bg-emerald-500 px-6 py-3 text-sm font-semibold text-slate-950 hover:bg-emerald-400"
                  >
                    Go to dashboard
                  </Link>
                ) : (
                  <>
                    <Link
                      to="/account/register"
                      className="rounded-md bg-emerald-500 px-6 py-3 text-sm font-semibold text-slate-950 hover:bg-emerald-400"
                    >
                      Create your account
                    </Link>
                    <Link
                      to="/login"
                      className="rounded-md border border-slate-700 px-6 py-3 text-sm font-semibold text-white hover:border-slate-500"
                    >
                      Sign in
                    </Link>
                  </>
                )}
              </div>
            </div>
          </div>
        </section>

        <section>
          <MagicBento
            glowColor="45, 212, 191"
            enableTilt
            cards={[
              {
                color: "#060010",
                title: "Invite codes",
                description: "Private servers with controlled access.",
                label: "Insights",
              },
              {
                color: "#060010",
                title: "Reward tiers",
                description: "Customize goals and commands for each tier.",
                label: "Overview",
              },
              {
                color: "#060010",
                title: "Push alerts",
                description: "Keep players informed with scheduled updates.",
                label: "Updates",
              },
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
              {
                color: "#060010",
                title: "Why communities love it",
                description:
                  "• Automated rewards and claim tracking\n" +
                  "• Real-time player tools and ban controls\n" +
                  "• Works with private invite-only servers",
                label: "Trust",
              },
              {
                color: "rgba(16, 185, 129, 0.12)",
                title: "Community admin",
                description:
                  "“We doubled daily activity in two weeks. Players log in just to hit the next step tier.”",
                label: "Community",
                className: "magic-bento-card--quote",
              },
            ]}
          />
        </section>
      </div>
    </Layout>
  );
}
