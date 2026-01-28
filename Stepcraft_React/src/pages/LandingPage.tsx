import { Link, useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { Layout } from "../components/Layout";
import { RevealText } from "../components/RevealText";
import Dock from "../components/Dock";
import ScrollReveal from "../components/ScrollReveal";
import { useAuthContext } from "../app/AuthContext";

export default function LandingPage() {
  const { isAuthenticated } = useAuthContext();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) {
      navigate("/dashboard", { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const dockItems = [
    {
      icon: "🚀",
      label: isAuthenticated ? "Dashboard" : "Create account",
      onClick: () => navigate(isAuthenticated ? "/dashboard" : "/account/register"),
    },
    {
      icon: "🔐",
      label: isAuthenticated ? "Sign out" : "Sign in",
      onClick: () => navigate(isAuthenticated ? "/dashboard" : "/login"),
    },
    {
      icon: "🧭",
      label: "Register server",
      onClick: () => navigate(isAuthenticated ? "/register" : "/login"),
    },
    {
      icon: "🎯",
      label: "Rewards",
      onClick: () => navigate(isAuthenticated ? "/dashboard" : "/login"),
    },
  ];

  return (
    <Layout title="Welcome">
      <div className="space-y-20">
        <section className="relative w-screen left-1/2 right-1/2 -ml-[50vw] -mr-[50vw] bg-slate-950 py-24">
          <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-slate-900/80 via-slate-950 to-slate-950" />
          <div className="pointer-events-none absolute -left-24 top-12 h-64 w-64 rounded-full bg-emerald-400/10 blur-3xl" />
          <div className="pointer-events-none absolute right-0 top-24 h-80 w-80 rounded-full bg-blue-400/10 blur-3xl" />
          <div className="relative mx-auto w-full max-w-screen-2xl px-6 2xl:px-10">
            <div className="max-w-3xl">
              <RevealText
                text="Inspire daily movement with a rewards-driven Minecraft server"
                className="text-5xl font-semibold text-white md:text-6xl"
              />
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
              <div className="mt-10">
                <Dock items={dockItems} />
              </div>
            </div>
          </div>
        </section>

        <section className="grid gap-8 lg:grid-cols-3">
          {[
            { title: "Invite codes", text: "Private servers with controlled access." },
            { title: "Reward tiers", text: "Customize goals and commands for each tier." },
            { title: "Push alerts", text: "Keep players informed with scheduled updates." },
          ].map((card) => (
            <div
              key={card.title}
              className="rounded-2xl border border-slate-800 bg-slate-950/60 p-6 text-slate-200 shadow-[0_0_0_1px_rgba(15,23,42,0.4)]"
            >
              <h3 className="text-lg font-semibold text-white">{card.title}</h3>
              <p className="mt-2 text-sm text-slate-400">{card.text}</p>
            </div>
          ))}
        </section>

        <section className="rounded-3xl border border-slate-800 bg-slate-950/70 p-8">
          <div className="flex flex-col gap-8 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <p className="text-xs uppercase tracking-[0.35em] text-slate-400">Launch sequence</p>
              <ScrollReveal containerClassName="mt-3" textClassName="text-3xl font-semibold text-white">
                Launch in 3 steps
              </ScrollReveal>
            </div>
            <div className="grid gap-6 text-sm text-slate-300 md:grid-cols-3">
              <div>
                <div className="text-xs uppercase text-emerald-300">01</div>
                <div className="mt-2 font-semibold text-white">Register your server</div>
                <p className="mt-1 text-slate-400">Get an API key and invite code instantly.</p>
              </div>
              <div>
                <div className="text-xs uppercase text-emerald-300">02</div>
                <div className="mt-2 font-semibold text-white">Set rewards</div>
                <p className="mt-1 text-slate-400">Configure commands per step tier.</p>
              </div>
              <div>
                <div className="text-xs uppercase text-emerald-300">03</div>
                <div className="mt-2 font-semibold text-white">Go live</div>
                <p className="mt-1 text-slate-400">Invite players and watch streaks grow.</p>
              </div>
            </div>
          </div>
        </section>

        <section className="grid gap-8 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="rounded-3xl border border-slate-800 bg-slate-950/70 p-8">
            <ScrollReveal textClassName="text-2xl font-semibold text-white">Why communities love it</ScrollReveal>
            <ul className="mt-6 space-y-3 text-sm text-slate-300">
              <li>• Automated rewards and claim tracking</li>
              <li>• Real-time player tools and ban controls</li>
              <li>• Works with private invite-only servers</li>
            </ul>
          </div>
          <div className="rounded-3xl border border-emerald-900/50 bg-emerald-900/20 p-8 text-emerald-200">
            <p className="text-lg leading-relaxed">
              “We doubled daily activity in two weeks. Players log in just to hit the next step tier.”
            </p>
            <div className="mt-6 text-xs uppercase tracking-[0.3em] text-emerald-300/70">Community Admin</div>
          </div>
        </section>
      </div>
    </Layout>
  );
}
