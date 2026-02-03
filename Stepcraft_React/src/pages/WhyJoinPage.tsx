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
  centerLabel = "StepCraft pitch",
}: {
  words: string[];
  centerLabel?: string;
}) {
  const sectionRef = useRef<HTMLElement | null>(null);
  const p = useSectionProgress(sectionRef);

  const track = useMemo(() => {
    const base = words.length ? words : ["Retention", "Habits", "Revenue", "Streaks", "Engagement"];
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
              <div className="text-xs uppercase tracking-[0.28em] text-emerald-300/80">Always-on loop</div>
              <div className="mt-2 text-2xl font-semibold text-white">{centerLabel}</div>
              <div className="mt-2 max-w-sm text-sm text-slate-300">
                Scroll-driven motion across the whole page (not just “fade in once”).
              </div>
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
  const primaryCtaLabel = isAuthenticated ? "Go to dashboard" : "Create your account";

  const scenes = useMemo(
    () => [
      {
        eyebrow: "Habit formation",
        title: "Daily logins feel rewarding.",
        body: "Players build streaks by hitting step goals, which increases the chance they return day after day.",
        imageAlt: "Streak reward UI",
        imageUrl: undefined,
      },
      {
        eyebrow: "Monetization",
        title: "More playtime, more purchases.",
        body: "Reward loops increase engagement, which leads to higher conversion on ranks, keys, and limited drops.",
        imageAlt: "Store or rank tier UI",
        imageUrl: undefined,
      },
      {
        eyebrow: "Direct contact",
        title: "A connection beyond Discord.",
        body: "Push notifications and app visibility give you a rare touchpoint, even when the player is not online.",
        imageAlt: "Push notification preview",
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
                  <p className="text-xs uppercase tracking-[0.28em] text-emerald-300/80">Why you should join</p>
                  <h1 className="mt-4 text-[2.6rem] font-semibold leading-tight text-white md:text-[3.4rem]">
                    StepCraft turns real-world movement into daily reasons to log back in.
                  </h1>
                  <p className="mt-5 text-base leading-relaxed text-slate-300 md:text-lg">
                    StepCraft builds player habits by rewarding steps with in-game progress. That habit loop increases
                    retention, boosts playtime, and opens new revenue opportunities for your server.
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
                </div>

                <div className="rounded-2xl border border-slate-800/70 bg-slate-950/50 p-6 shadow-[0_10px_30px_rgba(15,23,42,0.35)] backdrop-blur">
                  <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Elevator pitch</div>
                  <p className="mt-4 text-base leading-relaxed text-slate-200">
                    StepCraft gives your server an always-on connection to players through the phone app they already use
                    every day. By turning steps into rewards, you create daily streaks, higher retention, and more time
                    online—which directly raises revenue from ranks, boosts, and cosmetics.
                  </p>

                  <div className="mt-6 rounded-xl border border-slate-800/70 bg-slate-900/30 p-4 text-xs text-slate-400">
                    Replace with: mobile screenshots + dashboard collage.
                  </div>
                </div>
              </div>
            </section>

            {/* QUICK VALUE CARDS */}
            <section className="mt-10 grid gap-6 lg:grid-cols-3">
              {[
                {
                  title: "Retention loop",
                  body: "Daily step goals give players a reason to check in even when they are not thinking about the server.",
                },
                {
                  title: "Revenue lift",
                  body: "More sessions and longer playtime amplify rank upgrades, cosmetics, and other monetized perks.",
                },
                {
                  title: "Always-on contact",
                  body: "Few Minecraft mods live on a player's phone. StepCraft keeps your server top-of-mind off-platform.",
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

            {/* DRAG GALLERY */}
            <div className="mt-14">
              <DragGallery
                items={[
                  { label: "Home screen" },
                  { label: "Reward claim" },
                  { label: "Streak tracker" },
                  { label: "Server dashboard" },
                  { label: "Store / boosts" },
                ]}
              />
            </div>

            {/* BIG TYPO BAND */}
            <ScrollMarqueeBand
              words={["Retention", "Streaks", "Daily Goals", "Playtime", "Revenue", "Notifications", "Habit Loop"]}
              centerLabel="StepCraft pitch"
            />

            {/* DIFFERENTIATORS + VISUALS */}
            <section className="mt-16 grid gap-6 lg:grid-cols-[1.1fr_0.9fr] lg:items-start">
              <div className="rounded-2xl border border-slate-800/60 bg-slate-950/70 p-6">
                <h2 className="text-2xl font-semibold text-white">What makes StepCraft different?</h2>
                <ul className="mt-4 space-y-3 text-sm text-slate-300">
                  <li>- Real-world movement mapped directly to rewards and server stats.</li>
                  <li>- Daily step tiers create an always-on reason to return.</li>
                  <li>- Push notifications give server owners a direct contact point.</li>
                  <li>- Works for public hubs or invite-only communities.</li>
                </ul>
              </div>

              <div className="rounded-2xl border border-slate-800/60 bg-slate-950/60 p-6 text-slate-300">
                <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Visual impact</div>
                <p className="mt-3 text-sm">
                  Place screenshots of the mobile app, server dashboard, reward tiers, or player streaks here.
                </p>
                <div className="mt-6 grid gap-3 sm:grid-cols-2">
                  <div className="rounded-xl border border-slate-800/70 bg-slate-900/40 p-6 text-xs text-slate-400">
                    Image placeholder
                  </div>
                  <div className="rounded-xl border border-slate-800/70 bg-slate-900/40 p-6 text-xs text-slate-400">
                    Image placeholder
                  </div>
                </div>
              </div>
            </section>

            {/* FINAL CTA */}
            <section className="mt-16 rounded-2xl border border-slate-800 bg-slate-950/70 p-8 text-center">
              <h2 className="text-2xl font-semibold text-white">Bring retention and revenue together.</h2>
              <p className="mt-2 text-sm text-slate-400">
                Start your StepCraft server and build a community that logs in every day.
              </p>
              <div className="mt-6 flex justify-center">
                <Link
                  to={primaryCtaTo}
                  className="flex h-11 items-center justify-center rounded-md bg-emerald-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                >
                  {primaryCtaLabel}
                </Link>
              </div>
            </section>
          </div>
        </div>
      </SmoothScroll>
    </Layout>
  );
}
