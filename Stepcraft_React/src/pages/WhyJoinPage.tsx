import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";

type Card = {
  title: string;
  body: string;
  href: string;
};

type GridItem = {
  title: string;
  body: string;
  tag?: string;
};

type Step = {
  number: string;
  title: string;
  body: string;
};

type FAQ = {
  q: string;
  a: string;
};

function SectionHeader({
  kicker,
  title,
  body,
  right,
}: {
  kicker?: string;
  title: string;
  body?: string;
  right?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-6">
      <div className="max-w-2xl">
        {kicker ? (
          <p className="text-xs font-medium uppercase tracking-[0.28em] text-emerald-700/80">
            {kicker}
          </p>
        ) : null}
        <h2 className="mt-3 text-3xl font-semibold tracking-tight text-slate-900 md:text-4xl">
          {title}
        </h2>
        {body ? <p className="mt-3 text-base leading-relaxed text-slate-600">{body}</p> : null}
      </div>
      {right ? <div>{right}</div> : null}
    </div>
  );
}

function ShellCard({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-3xl border border-slate-200 bg-white shadow-[0_8px_30px_rgba(15,23,42,0.08)]">
      {children}
    </div>
  );
}

export default function WhyJoinPage() {
  const { isAuthenticated } = useAuthContext();

  const primaryCtaTo = isAuthenticated ? "/dashboard" : "/account/register";
  const primaryCtaLabel = isAuthenticated ? "Go to dashboard" : "Create your account";

  const solutionCards: Card[] = [
    {
      title: "Retention loop",
      body: "Daily step goals give players a reason to check in even when they are not thinking about the server.",
      href: "#retention",
    },
    {
      title: "Revenue lift",
      body: "More sessions and longer playtime amplify rank upgrades, cosmetics, and other monetized perks.",
      href: "#revenue",
    },
    {
      title: "Always-on contact",
      body: "Few Minecraft mods live on a player's phone. StepCraft keeps your server top-of-mind off-platform.",
      href: "#contact-point",
    },
  ];

  const pillars = [
    "Habit formation",
    "Daily streaks",
    "Rewards",
    "Retention",
    "Notifications",
    "Playtime lift",
    "Community",
    "Server growth",
  ];

  const newCapabilities: GridItem[] = [
    {
      tag: "New",
      title: "Habit formation",
      body: "Players build streaks by hitting step goals, which increases the chance they return day after day.",
    },
    {
      tag: "New",
      title: "Monetization",
      body: "Reward loops increase engagement, which leads to higher conversion on ranks, keys, and limited drops.",
    },
    {
      tag: "New",
      title: "Direct contact",
      body: "Push notifications and app visibility give you a rare touchpoint, even when the player is not online.",
    },
    {
      title: "Works for any community style",
      body: "Fits public hubs, invite-only communities, or small friend servers without changing your identity.",
    },
  ];

  const steps: Step[] = [
    {
      number: "01",
      title: "Set the goal",
      body: "Choose daily step tiers that feel attainable and rewardable for your players.",
    },
    {
      number: "02",
      title: "Connect the loop",
      body: "Steps convert into progress so there’s always a reason to log in and claim rewards.",
    },
    {
      number: "03",
      title: "Reinforce with streaks",
      body: "Streaks turn casual activity into a daily habit that keeps your server top-of-mind.",
    },
    {
      number: "04",
      title: "Scale the impact",
      body: "Better retention and longer sessions create more opportunity for monetization and community growth.",
    },
  ];

  const faqs: FAQ[] = [
    {
      q: "Is StepCraft only for big servers?",
      a: "No. The loop works for small friend servers and large public hubs. The key is consistent rewards and clear daily goals.",
    },
    {
      q: "Does this replace Discord?",
      a: "No. Think of it as an additional touchpoint that lives where players already are every day: their phone.",
    },
    {
      q: "What should I show when pitching StepCraft?",
      a: "Lead with retention (daily habit), then the revenue lift (more sessions and playtime), then the always-on contact point.",
    },
    {
      q: "What screenshots should go on this page?",
      a: "App home, reward claim, streak screen, and a server/admin dashboard view (even mockups are fine).",
    },
  ];

  return (
    <Layout>
      <div className="bg-white">
        {/* HERO */}
        <section className="mx-auto max-w-6xl px-6 pt-12 pb-10 md:pt-16">
          <div className="grid gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:items-start">
            <div>
              <p className="text-xs font-medium uppercase tracking-[0.28em] text-emerald-700/80">
                Why you should join
              </p>

              <h1 className="mt-4 text-4xl font-semibold tracking-tight text-slate-900 md:text-5xl md:leading-[1.05]">
                StepCraft turns real-world movement into daily reasons to log back in.
              </h1>

              <p className="mt-5 max-w-2xl text-base leading-relaxed text-slate-600 md:text-lg">
                StepCraft builds player habits by rewarding steps with in-game progress. That habit loop increases
                retention, boosts playtime, and opens new revenue opportunities for your server.
              </p>

              <div className="mt-8 flex flex-wrap gap-3">
                <Link
                  to={primaryCtaTo}
                  className="inline-flex h-11 items-center justify-center rounded-full bg-slate-900 px-6 text-sm font-semibold text-white shadow-sm transition hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                >
                  {primaryCtaLabel}
                </Link>

                <Link
                  to="/"
                  className="inline-flex h-11 items-center justify-center rounded-full border border-slate-200 bg-white px-6 text-sm font-semibold text-slate-900 transition hover:border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                >
                  Back to landing
                </Link>
              </div>

              {/* “Packaging solutions” style cards */}
              <div className="mt-10 grid gap-4 md:grid-cols-3">
                {solutionCards.map((c) => (
                  <a
                    key={c.title}
                    href={c.href}
                    className="group rounded-3xl border border-slate-200 bg-white p-6 shadow-[0_8px_30px_rgba(15,23,42,0.05)] transition hover:border-slate-300"
                  >
                    <div className="text-xs font-medium uppercase tracking-[0.22em] text-emerald-700/80">
                      {c.title}
                    </div>
                    <p className="mt-3 text-sm leading-relaxed text-slate-600">{c.body}</p>
                    <div className="mt-5 text-sm font-semibold text-slate-900">
                      Tell me more <span className="transition group-hover:translate-x-0.5 inline-block">→</span>
                    </div>
                  </a>
                ))}
              </div>
            </div>

            {/* Right “Elevator pitch” panel */}
            <ShellCard>
              <div className="p-7">
                <div className="text-xs font-medium uppercase tracking-[0.22em] text-slate-500">
                  Elevator pitch
                </div>
                <p className="mt-4 text-base leading-relaxed text-slate-700">
                  StepCraft gives your server an always-on connection to players through the phone app they already use
                  every day. By turning steps into rewards, you create daily streaks, higher retention, and more time
                  online—which directly raises revenue from ranks, boosts, and cosmetics.
                </p>

                <div className="mt-6 rounded-2xl border border-slate-200 bg-slate-50 p-4 text-xs text-slate-500">
                  Image placeholder: app home screen, reward claim screen, or server dashboard.
                </div>
              </div>
            </ShellCard>
          </div>
        </section>

        {/* PILLARS STRIP */}
        <section className="border-y border-slate-200 bg-white">
          <div className="mx-auto max-w-6xl px-6 py-6">
            <div className="flex flex-wrap gap-2">
              {pillars.map((p) => (
                <span
                  key={p}
                  className="rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-sm text-slate-700"
                >
                  {p}
                </span>
              ))}
            </div>
          </div>
        </section>

        {/* COMMITTED + MISSION/VISION STYLE */}
        <section className="mx-auto max-w-6xl px-6 py-14">
          <SectionHeader
            kicker="Committed"
            title="Committed to retention, always reinforcing the habit."
            body="Use these blocks to explain what makes StepCraft different at a glance—then back it up with visuals."
            right={
              <Link
                to={primaryCtaTo}
                className="inline-flex h-10 items-center justify-center rounded-full bg-emerald-600 px-5 text-sm font-semibold text-white transition hover:bg-emerald-500"
              >
                {primaryCtaLabel}
              </Link>
            }
          />

          <div className="mt-8 grid gap-6 lg:grid-cols-[1.1fr_0.9fr] lg:items-start">
            <ShellCard>
              <div className="p-7">
                <h3 className="text-xl font-semibold text-slate-900">What makes StepCraft different?</h3>
                <ul className="mt-4 space-y-3 text-sm text-slate-600">
                  <li>• Real-world movement mapped directly to rewards and server stats.</li>
                  <li>• Daily step tiers create an always-on reason to return.</li>
                  <li>• Push notifications give server owners a direct contact point.</li>
                  <li>• Works for public hubs or invite-only communities.</li>
                </ul>
              </div>
            </ShellCard>

            <ShellCard>
              <div className="p-7">
                <div className="text-xs font-medium uppercase tracking-[0.22em] text-slate-500">
                  Visual impact
                </div>
                <p className="mt-3 text-sm text-slate-600">
                  Place screenshots of the mobile app, server dashboard, reward tiers, or player streaks here.
                </p>
                <div className="mt-6 grid gap-3 sm:grid-cols-2">
                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 text-xs text-slate-500">
                    Image placeholder
                  </div>
                  <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 text-xs text-slate-500">
                    Image placeholder
                  </div>
                </div>
              </div>
            </ShellCard>
          </div>
        </section>

        {/* NEW CAPABILITIES GRID */}
        <section className="mx-auto max-w-6xl px-6 pb-14">
          <SectionHeader
            title="New capabilities to highlight"
            body="This is your “New Products” grid, but translated into pitchable outcomes and features."
            right={
              <a
                href="#gallery"
                className="text-sm font-semibold text-slate-900 hover:text-slate-700"
              >
                See examples →
              </a>
            }
          />

          <div className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            {newCapabilities.map((f) => (
              <ShellCard key={f.title}>
                <div className="p-5">
                  <div className="h-24 rounded-2xl border border-slate-200 bg-slate-50" />
                  <div className="mt-4 flex items-center justify-between gap-3">
                    <div className="font-semibold text-slate-900">{f.title}</div>
                    {f.tag ? (
                      <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-800">
                        {f.tag}
                      </span>
                    ) : null}
                  </div>
                  <p className="mt-2 text-sm leading-relaxed text-slate-600">{f.body}</p>
                </div>
              </ShellCard>
            ))}
          </div>
        </section>

        {/* PROCESS */}
        <section className="border-t border-slate-200 bg-slate-50">
          <div className="mx-auto max-w-6xl px-6 py-14">
            <SectionHeader
              kicker="Process"
              title="A simple 4-step loop that drives daily logins"
              body="Modeled after a “process” section: short, numbered, and easy to scan."
            />

            <div className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              {steps.map((s) => (
                <ShellCard key={s.number}>
                  <div className="p-6">
                    <div className="text-xs font-semibold text-slate-500">{s.number}</div>
                    <div className="mt-2 text-lg font-semibold text-slate-900">{s.title}</div>
                    <p className="mt-3 text-sm leading-relaxed text-slate-600">{s.body}</p>
                  </div>
                </ShellCard>
              ))}
            </div>

            {/* Custom Solutions-style CTA band */}
            <div className="mt-10 rounded-[32px] border border-slate-200 bg-white p-8 shadow-[0_8px_30px_rgba(15,23,42,0.06)]">
              <div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
                <div>
                  <div className="text-xl font-semibold text-slate-900">
                    Bring retention and revenue together.
                  </div>
                  <div className="mt-2 text-sm text-slate-600">
                    Start your StepCraft server and build a community that logs in every day.
                  </div>
                </div>
                <Link
                  to={primaryCtaTo}
                  className="inline-flex h-11 items-center justify-center rounded-full bg-slate-900 px-6 text-sm font-semibold text-white transition hover:bg-slate-800"
                >
                  {primaryCtaLabel}
                </Link>
              </div>
            </div>
          </div>
        </section>

        {/* GET INSPIRED / GALLERY */}
        <section id="gallery" className="mx-auto max-w-6xl px-6 py-14">
          <SectionHeader
            kicker="Get inspired"
            title="Show the story visually"
            body="Use a horizontal gallery to match the “drag to explore” feel."
          />

          <div className="mt-8 -mx-6 px-6">
            <div className="flex gap-4 overflow-x-auto pb-4 snap-x snap-mandatory">
              {[
                "App home screen",
                "Reward claim",
                "Streak screen",
                "Server dashboard",
                "Push notification",
              ].map((label) => (
                <div
                  key={label}
                  className="min-w-[280px] max-w-[280px] snap-start rounded-3xl border border-slate-200 bg-white shadow-[0_8px_30px_rgba(15,23,42,0.06)]"
                >
                  <div className="p-5">
                    <div className="h-40 rounded-2xl border border-slate-200 bg-slate-50" />
                    <div className="mt-4 text-sm font-semibold text-slate-900">{label}</div>
                    <div className="mt-1 text-xs text-slate-500">Image placeholder</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* FAQ */}
        <section className="border-t border-slate-200 bg-white">
          <div className="mx-auto max-w-6xl px-6 py-14">
            <SectionHeader
              kicker="FAQ"
              title="Questions that come up in a pitch"
              body="Keep these short. The goal is confidence, not a wall of text."
            />

            <div className="mt-8 grid gap-4 md:grid-cols-2">
              {faqs.map((f) => (
                <ShellCard key={f.q}>
                  <div className="p-6">
                    <div className="text-sm font-semibold text-slate-900">{f.q}</div>
                    <div className="mt-2 text-sm leading-relaxed text-slate-600">{f.a}</div>
                  </div>
                </ShellCard>
              ))}
            </div>
          </div>
        </section>

        {/* FINAL CTA */}
        <section className="border-t border-slate-200 bg-slate-50">
          <div className="mx-auto max-w-6xl px-6 py-14 text-center">
            <h2 className="text-3xl font-semibold tracking-tight text-slate-900">
              Ready to pitch StepCraft with confidence?
            </h2>
            <p className="mt-3 text-base text-slate-600">
              Keep the message simple: habit → retention → revenue.
            </p>
            <div className="mt-7 flex justify-center">
              <Link
                to={primaryCtaTo}
                className="inline-flex h-11 items-center justify-center rounded-full bg-emerald-600 px-7 text-sm font-semibold text-white transition hover:bg-emerald-500"
              >
                {primaryCtaLabel}
              </Link>
            </div>
          </div>
        </section>
      </div>
    </Layout>
  );
}
