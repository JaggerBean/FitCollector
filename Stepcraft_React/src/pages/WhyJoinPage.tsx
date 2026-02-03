import { useMemo, useRef, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import SmoothScroll from "../components/SmoothScroll";
import { PitchScrollScene } from "../components/ScrollStack";
import { DragGallery } from "../components/DraggableGallery";
import { useAuthContext } from "../app/AuthContext";

function clamp01(v: number) {
  return Math.min(1, Math.max(0, v));
}

function useSectionProgress(sectionRef: React.RefObject<HTMLElement | null>) {
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    let raf = 0;

    const tick = () => {
      const el = sectionRef.current;
      if (!el) {
        raf = requestAnimationFrame(tick);
        return;
      }

      const rect = el.getBoundingClientRect();
      const vh = window.innerHeight || 1;
      const totalScrollable = Math.max(1, rect.height - vh);
      const scrolled = clamp01((-rect.top) / totalScrollable);

      setProgress(scrolled);
      raf = requestAnimationFrame(tick);
    };

    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [sectionRef]);

  return progress;
}

function ScrollMarqueeBand({
  words,
  centerLabel = "Built for retention",
  subLabel = "Turn real-world movement into repeat play sessions.",
}: {
  words: string[];
  centerLabel?: string;
  subLabel?: string;
}) {
  const sectionRef = useRef<HTMLElement | null>(null);
  const p = useSectionProgress(sectionRef);

  const track = useMemo(() => {
    const base = words.length ? words : ["Retention", "Streaks", "Sessions", "ARPU", "Reactivation"];
    return [...base, ...base, ...base];
  }, [words]);

  const x = -(p * 40);

  return (
    <section ref={sectionRef} className="relative mt-16" style={{ height: "140vh" }}>
      <div className="sticky top-0 overflow-hidden rounded-3xl border border-slate-800/70 bg-slate-950 py-16">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.16),transparent_55%)]" />
        <div className="relative">
          <div
            className="whitespace-nowrap text-[11vw] font-semibold tracking-tight text-white/90 md:text-[7vw]"
            style={{
              transform: `translate3d(${x}vw, 0, 0)`,
              transition: "transform 40ms linear",
            }}
          >
            {track.map((w, i) => (
              <span key={`${w}-${i}`} className="mx-[2.2vw] inline-block">
                {w}
              </span>
            ))}
          </div>

          <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
            <div
              className="rounded-3xl border border-slate-200/15 bg-slate-950/30 px-8 py-6 text-center backdrop-blur"
              style={{
                transform: `translateY(${(0.5 - p) * 16}px) scale(${1 + Math.sin(p * Math.PI) * 0.02})`,
                transition: "transform 120ms ease",
              }}
            >
              <div className="text-xs uppercase tracking-[0.28em] text-emerald-300/80">{centerLabel}</div>
              <div className="mt-2 max-w-md text-sm text-slate-300">{subLabel}</div>
            </div>
          </div>

          <div className="pointer-events-none absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-slate-950 to-transparent" />
          <div className="pointer-events-none absolute inset-x-0 top-0 h-24 bg-gradient-to-b from-slate-950 to-transparent" />
        </div>
      </div>
    </section>
  );
}

export default function WhyJoinPage() {
  const { isAuthenticated } = useAuthContext();

  const primaryCtaTo = isAuthenticated ? "/dashboard" : "/account/register";
  const primaryCtaLabel = isAuthenticated ? "Go to dashboard" : "Get started";

  const scenes = useMemo(
    () => [
      {
        eyebrow: "Retention engine",
        title: "Build a daily habit that pulls players back in.",
        body: "StepCraft turns real-world steps into in-game progress. Players get a reason to show up every day, even when they weren’t planning to play. More days played means less churn and stronger communities.",
        imageAlt: "Daily streak rewards",
        imageUrl: undefined,
      },
      {
        eyebrow: "Revenue lift",
        title: "More sessions → more purchases.",
        body: "When players log in more often, they buy more often. StepCraft increases playtime and repeat visits, which raises conversion on ranks, boosters, cosmetics, crates, and seasonal offers.",
        imageAlt: "Store and rank upgrades",
        imageUrl: undefined,
      },
      {
        eyebrow: "Point of contact",
        title: "Your server stays visible off-platform.",
        body: "Most servers rely on Discord and hope players notice pings. StepCraft adds a phone app touchpoint—something players see every day—so your server stays top-of-mind even when they’re not online.",
        imageAlt: "Phone notifications and app presence",
        imageUrl: undefined,
      },
      {
        eyebrow: "Reactivation",
        title: "Bring back players who drift away.",
        body: "Players lapse. StepCraft gives you reactivation moments: missed streaks, weekly goals, and reward reminders that nudge players back into the server—without you spamming Discord.",
        imageAlt: "Reactivation reminders",
        imageUrl: undefined,
      },
    ],
    [],
  );

  return (
    <Layout>
      <SmoothScroll>
        <div className="bg-slate-950">
          <div className="mx-auto max-w-6xl px-4 py-10 md:px-6 md:py-14">
            {/* HERO */}
            <section className="relative overflow-hidden rounded-3xl border border-slate-800/70 bg-slate-950/95 px-6 py-14 md:px-10">
              <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.14),transparent_55%)]" />

              <div className="relative grid gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
                <div>
                  <p className="text-xs uppercase tracking-[0.28em] text-emerald-300/80">
                    Why StepCraft for your server
                  </p>

                  <h1 className="mt-4 text-[2.6rem] font-semibold leading-tight text-white md:text-[3.4rem]">
                    A retention system players can’t ignore—because it lives on their phone.
                  </h1>

                  <p className="mt-5 text-base leading-relaxed text-slate-300 md:text-lg">
                    StepCraft turns real-world movement into in-game progress. Players build streaks, chase daily goals,
                    and come back more often. For server owners, that means higher retention, longer sessions, and more
                    opportunities to monetize without feeling pay-to-win.
                  </p>

                  <div className="mt-8 flex flex-wrap gap-3">
                    <Link
                      to={primaryCtaTo}
                      className="flex h-11 items-center justify-center rounded-md bg-emerald-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                    >
                      {primaryCtaLabel}
                    </Link>

                    <Link
                      to="/"
                      className="flex h-11 items-center justify-center rounded-md border border-slate-700 px-6 text-sm font-semibold text-white transition hover:border-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                    >
                      Back to landing
                    </Link>
                  </div>

                  <div className="mt-6 grid gap-3 sm:grid-cols-3">
                    <div className="rounded-xl border border-slate-800/70 bg-slate-950/55 p-4">
                      <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Outcome</div>
                      <div className="mt-1 text-sm font-semibold text-white">More days played</div>
                      <div className="mt-1 text-xs text-slate-400">Fewer players drifting away.</div>
                    </div>
                    <div className="rounded-xl border border-slate-800/70 bg-slate-950/55 p-4">
                      <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Outcome</div>
                      <div className="mt-1 text-sm font-semibold text-white">More sessions</div>
                      <div className="mt-1 text-xs text-slate-400">More chances to convert.</div>
                    </div>
                    <div className="rounded-xl border border-slate-800/70 bg-slate-950/55 p-4">
                      <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Outcome</div>
                      <div className="mt-1 text-sm font-semibold text-white">More touchpoints</div>
                      <div className="mt-1 text-xs text-slate-400">Your server stays visible.</div>
                    </div>
                  </div>
                </div>

                <div className="rounded-2xl border border-slate-800/70 bg-slate-950/50 p-6 shadow-[0_10px_30px_rgba(15,23,42,0.35)] backdrop-blur">
                  <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Owner pitch (copy/paste)</div>

                  <p className="mt-4 text-base leading-relaxed text-slate-200">
                    StepCraft increases retention by giving players daily goals that reward real-world steps with in-game
                    progress. Players build streaks and return more often, which boosts playtime and raises conversion on
                    ranks, cosmetics, boosters, and seasonal offers. Unlike most mods, StepCraft adds a phone app
                    touchpoint, keeping your server top-of-mind even when players aren’t online.
                  </p>

                  <div className="mt-6 rounded-xl border border-slate-800/70 bg-slate-900/30 p-4 text-xs text-slate-300">
                    Tip: add 2–3 screenshots here (streaks, reward claim, server dashboard) and this becomes a shareable
                    one-pager for owners.
                  </div>
                </div>
              </div>
            </section>

            {/* KEY BENEFITS */}
            <section className="mt-10 grid gap-6 lg:grid-cols-3">
              {[
                {
                  title: "Retention loop",
                  body: "Daily step goals create a habit. Players show up because they don’t want to break streaks.",
                },
                {
                  title: "Profit lift",
                  body: "More days played means more store exposure and higher conversion on monetized perks.",
                },
                {
                  title: "Off-platform visibility",
                  body: "A phone app is a rare advantage. Players see it every day—your server stays top-of-mind.",
                },
              ].map((item) => (
                <div
                  key={item.title}
                  className="rounded-2xl border border-slate-800/60 bg-slate-950/70 p-6 text-slate-200"
                >
                  <div className="text-xs uppercase tracking-[0.2em] text-emerald-300/70">{item.title}</div>
                  <p className="mt-3 text-sm leading-relaxed text-slate-300">{item.body}</p>
                </div>
              ))}
            </section>

            {/* PINNED SCROLL STORY */}
            <div className="mt-14">
              <PitchScrollScene scenes={scenes} />
            </div>

            {/* DRAG GALLERY (SELLS “REAL PRODUCT”, NOT PLACEHOLDER) */}
            <div className="mt-14 rounded-3xl border border-slate-800/70 bg-slate-950/70 p-8">
              <div className="flex flex-wrap items-end justify-between gap-6">
                <div>
                  <div className="text-xs uppercase tracking-[0.28em] text-emerald-300/80">What players experience</div>
                  <h2 className="mt-3 text-2xl font-semibold text-white">A simple loop: steps → rewards → login.</h2>
                  <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-300">
                    Players don’t need a tutorial. They hit a goal, claim a reward, and feel progress. That loop is what
                    keeps communities alive—especially between big updates.
                  </p>
                </div>

                <div className="rounded-full border border-slate-800/80 bg-slate-950/40 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-400">
                  Drag to explore
                </div>
              </div>

              <div className="mt-8">
                <DragGallery
                  items={[
                    { label: "Daily goals & streaks" },
                    { label: "Reward claim flow" },
                    { label: "Progress tiers" },
                    { label: "Server offers (ranks/boosts)" },
                    { label: "Owner dashboard insights" },
                  ]}
                />
              </div>

              <div className="mt-6 grid gap-3 md:grid-cols-3">
                {[
                  {
                    h: "Not pay-to-win",
                    p: "Rewards can be balanced around convenience and cosmetics—engagement without backlash.",
                  },
                  {
                    h: "Seasonal hooks",
                    p: "Tie goals to events, weekends, and limited drops to create predictable spikes in activity.",
                  },
                  {
                    h: "Community stickiness",
                    p: "Daily shared goals create conversation, competition, and “I’ll hop on” moments.",
                  },
                ].map((x) => (
                  <div key={x.h} className="rounded-2xl border border-slate-800/60 bg-slate-950/60 p-5">
                    <div className="text-sm font-semibold text-white">{x.h}</div>
                    <div className="mt-2 text-sm text-slate-300">{x.p}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* BIG TYPO BAND */}
            <ScrollMarqueeBand
              words={["Retention", "Streaks", "Sessions", "Conversion", "ARPU", "Reactivation", "Touchpoints"]}
              centerLabel="Built for server owners"
              subLabel="More repeat play, more store exposure, and a direct line to players via the phone app."
            />

            {/* DIFFERENTIATORS + IMPLEMENTATION ANGLES */}
            <section className="mt-16 grid gap-6 lg:grid-cols-[1.1fr_0.9fr] lg:items-start">
              <div className="rounded-2xl border border-slate-800/60 bg-slate-950/70 p-6">
                <h2 className="text-2xl font-semibold text-white">Why this works when Discord doesn’t</h2>

                <div className="mt-4 space-y-4 text-sm text-slate-300">
                  <div>
                    <div className="text-xs uppercase tracking-[0.2em] text-slate-400">The problem</div>
                    <p className="mt-1">
                      Most servers fight churn with announcements and pings. Players mute channels, miss messages, or
                      simply drift away.
                    </p>
                  </div>

                  <div>
                    <div className="text-xs uppercase tracking-[0.2em] text-slate-400">StepCraft advantage</div>
                    <p className="mt-1">
                      StepCraft is a daily device touchpoint. Players see it on their phone—often multiple times per
                      day—so your server stays present even when they aren’t thinking about Minecraft.
                    </p>
                  </div>

                  <div>
                    <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Owner impact</div>
                    <ul className="mt-2 space-y-2">
                      <li>- Higher retention from streaks and daily goals.</li>
                      <li>- Higher conversion from more sessions and store exposure.</li>
                      <li>- Cleaner marketing: fewer “spammy” pings, more meaningful nudges.</li>
                      <li>- Reactivation hooks for players who drift away.</li>
                    </ul>
                  </div>
                </div>
              </div>

              <div className="rounded-2xl border border-slate-800/60 bg-slate-950/60 p-6 text-slate-300">
                <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Use cases owners care about</div>

                <div className="mt-4 space-y-4">
                  {[
                    {
                      h: "Rank & cosmetic lift",
                      p: "Higher play frequency increases conversion without changing your store at all.",
                    },
                    {
                      h: "Weekend events",
                      p: "Boost step multipliers for events to create predictable spikes in activity and revenue.",
                    },
                    {
                      h: "Seasonal retention",
                      p: "Daily goals keep players engaged between major content drops when churn normally rises.",
                    },
                    {
                      h: "New player onboarding",
                      p: "Give fresh players a clear daily path so they don’t bounce after day one.",
                    },
                  ].map((x) => (
                    <div key={x.h} className="rounded-xl border border-slate-800/70 bg-slate-900/30 p-4">
                      <div className="text-sm font-semibold text-white">{x.h}</div>
                      <div className="mt-1 text-sm text-slate-300">{x.p}</div>
                    </div>
                  ))}
                </div>
              </div>
            </section>

            {/* FINAL CTA */}
            <section className="mt-16 rounded-2xl border border-slate-800 bg-slate-950/70 p-8 text-center">
              <h2 className="text-2xl font-semibold text-white">Turn daily movement into daily logins.</h2>
              <p className="mt-2 text-sm text-slate-400">
                If you want higher retention, more sessions, and a new off-platform touchpoint, StepCraft is built for
                you.
              </p>
              <div className="mt-6 flex justify-center">
                <Link
                  to={primaryCtaTo}
                  className="flex h-11 items-center justify-center rounded-md bg-emerald-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                >
                  {primaryCtaLabel}
                </Link>
              </div>
              <div className="mt-4 text-xs text-slate-500">
                Want a quick owner demo? Add 2–3 screenshots above and share this page as your pitch.
              </div>
            </section>
          </div>
        </div>
      </SmoothScroll>
    </Layout>
  );
}
