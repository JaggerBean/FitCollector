import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";

// Adjust these two imports to match whatever you exported in those files.
import { PitchScrollScene } from "../components/AnimatedList";
import { DraggableGallery } from "../components/DraggableGallery";

type Card = { title: string; body: string; href: string };

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
          <p className="text-xs font-medium uppercase tracking-[0.28em] text-emerald-700/80">{kicker}</p>
        ) : null}
        <h2 className="mt-3 text-3xl font-semibold tracking-tight text-slate-900 md:text-4xl">{title}</h2>
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

              {/* 3 “solutions” cards */}
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
                      Tell me more <span className="inline-block transition group-hover:translate-x-0.5">→</span>
                    </div>
                  </a>
                ))}
              </div>
            </div>

            {/* Elevator pitch */}
            <ShellCard>
              <div className="p-7">
                <div className="text-xs font-medium uppercase tracking-[0.22em] text-slate-500">Elevator pitch</div>
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

        {/* MOTION / SCROLL STORY (replaces old ScrollStack section) */}
        <section className="mx-auto max-w-6xl px-6 py-14">
          <SectionHeader
            kicker="Key ideas to pitch"
            title="A fluid scroll story you can present in 30 seconds"
            body="This is the interactive section: as you scroll, the talking points and visuals progress like a mini narrative."
            right={
              <div className="rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-500">
                Scroll
              </div>
            }
          />

          <div className="mt-8">
            <PitchScrollScene
              scenes={[
                {
                  eyebrow: "Habit formation",
                  title: "Daily logins feel rewarding.",
                  body: "Players build streaks by hitting step goals, which increases the chance they return day after day.",
                  imageAlt: "Streak reward UI",
                },
                {
                  eyebrow: "Monetization",
                  title: "More playtime, more purchases.",
                  body: "Reward loops increase engagement, which leads to higher conversion on ranks, keys, and limited drops.",
                  imageAlt: "Store items / rank tiers",
                },
                {
                  eyebrow: "Direct contact",
                  title: "A connection beyond Discord.",
                  body: "Push notifications and app visibility give you a rare touchpoint, even when the player is not online.",
                  imageAlt: "Push notification preview",
                },
              ]}
            />
          </div>
        </section>

        {/* COMMITTED + “WHAT’S DIFFERENT” */}
        <section className="mx-auto max-w-6xl px-6 pb-14">
          <SectionHeader
            kicker="Committed"
            title="Committed to retention, always reinforcing the habit."
            body="These blocks explain what makes StepCraft different at a glance—then you back it up with visuals."
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
                <div className="text-xs font-medium uppercase tracking-[0.22em] text-slate-500">Visual impact</div>
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

        {/* DRAGGABLE GALLERY (interactive) */}
        <section className="mx-auto max-w-6xl px-6 pb-14">
          <SectionHeader
            kicker="Get inspired"
            title="Screens & moments"
            body="This adds that “interactive” feel: users can drag through examples instead of just reading."
          />

          <div className="mt-8">
            <DraggableGallery
              items={[
                { label: "App home (steps + goal)" },
                { label: "Reward claim screen" },
                { label: "Streak milestone screen" },
                { label: "Server dashboard snapshot" },
                { label: "Push notification preview" },
              ]}
            />
          </div>
        </section>

        {/* FINAL CTA */}
        <section className="border-t border-slate-200 bg-slate-50">
          <div className="mx-auto max-w-6xl px-6 py-14 text-center">
            <h2 className="text-3xl font-semibold tracking-tight text-slate-900">
              Bring retention and revenue together.
            </h2>
            <p className="mt-3 text-base text-slate-600">
              Start your StepCraft server and build a community that logs in every day.
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
