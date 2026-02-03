import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import SmoothScroll from "../components/SmoothScroll";
import { ScrollStack, ScrollStackItem } from "../components/ScrollStack";
import { useAuthContext } from "../app/AuthContext";
import { useEffect, useMemo, useRef, useState } from "react";

type ShowcaseSlide = {
  kicker: string;
  title: string;
  body: string;
  cta: string;
  imageUrl: string;
};

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

function StickyShowcase({
  slides,
  themeClassName = "",
}: {
  slides: ShowcaseSlide[];
  themeClassName?: string;
}) {
  const sectionRef = useRef<HTMLElement | null>(null);
  const p = useSectionProgress(sectionRef);

  const total = slides.length;
  const scaled = p * total;
  const active = Math.min(total - 1, Math.floor(scaled));
  const intra = clamp01(scaled - active);

  const prevIdx = Math.max(0, active - 1);
  const nextIdx = Math.min(total - 1, active + 1);

  return (
    <section
      ref={sectionRef}
      className={`relative ${themeClassName}`}
      style={{ height: `${Math.max(2, slides.length + 1) * 100}vh` }}
    >
      <div className="sticky top-0 h-screen w-full overflow-hidden rounded-3xl border border-slate-800/70 bg-slate-950">
        <div className="absolute inset-0">
          {slides.map((s, idx) => {
            const isActive = idx === active;
            const isNext = idx === nextIdx;

            const opacity = isActive ? 1 : isNext ? intra : 0;
            const scale = 1 + (isActive ? (1 - intra) * 0.03 : 0);

            return (
              <div
                key={s.title}
                className="absolute inset-0 transition-opacity duration-300"
                style={{ opacity }}
              >
                <div
                  className="absolute inset-0"
                  style={{
                    backgroundImage: `url(${s.imageUrl})`,
                    backgroundSize: "cover",
                    backgroundPosition: "center",
                    transform: `scale(${scale})`,
                    transition: "transform 300ms ease",
                  }}
                />
                <div className="absolute inset-0 bg-gradient-to-b from-slate-950/30 via-slate-950/55 to-slate-950/80" />
              </div>
            );
          })}
        </div>

        <div className="relative mx-auto flex h-full w-full max-w-6xl flex-col px-6 py-10 md:px-10">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <div className="text-xs uppercase tracking-[0.28em] text-emerald-300/80">
                Packaging-style scroll scenes, but for StepCraft
              </div>
              <h2 className="mt-3 text-3xl font-semibold tracking-tight text-white md:text-4xl">
                Scroll through the pitch like chapters.
              </h2>
              <p className="mt-3 max-w-2xl text-sm leading-relaxed text-slate-300 md:text-base">
                This section is a pinned “scene”. The background swaps and the cards slide/scale as your scroll advances,
                so it feels like moving through different pages.
              </p>
            </div>

            <div className="rounded-full border border-slate-800/80 bg-slate-950/40 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-300 backdrop-blur">
              Scene {active + 1} / {slides.length}
            </div>
          </div>

          <div className="mt-10 flex flex-1 items-center">
            <div className="grid w-full gap-6 md:grid-cols-3">
              <div
                className="hidden h-[360px] rounded-3xl border border-white/10 bg-white/5 p-6 text-white/90 backdrop-blur md:block"
                style={{
                  transform: `translateY(${(intra - 1) * 10}px) scale(${0.96})`,
                  transition: "transform 220ms ease",
                }}
              >
                <div className="text-xs uppercase tracking-[0.2em] text-white/60">Previous</div>
                <div className="mt-4 text-xl font-semibold">{slides[prevIdx].title}</div>
                <div className="mt-3 text-sm leading-relaxed text-white/70">{slides[prevIdx].body}</div>
              </div>

              <div
                className="relative h-[360px] rounded-3xl border border-slate-200/20 bg-slate-50/95 p-7 text-slate-950 shadow-[0_30px_80px_rgba(0,0,0,0.35)]"
                style={{
                  transform: `translateY(${(1 - intra) * 8}px) scale(${1 + (1 - intra) * 0.01})`,
                  transition: "transform 220ms ease",
                }}
              >
                <div className="text-xs uppercase tracking-[0.24em] text-slate-600">{slides[active].kicker}</div>
                <div className="mt-4 text-3xl font-semibold tracking-tight">{slides[active].title}</div>
                <p className="mt-4 text-sm leading-relaxed text-slate-700">{slides[active].body}</p>

                <div className="absolute inset-x-7 bottom-7">
                  <div className="mb-4 h-px w-full bg-slate-200" />
                  <div className="flex items-center justify-between text-sm text-slate-800">
                    <span>{slides[active].cta}</span>
                    <span aria-hidden className="text-lg">
                      →
                    </span>
                  </div>
                </div>
              </div>

              <div
                className="hidden h-[360px] rounded-3xl border border-white/10 bg-white/5 p-6 text-white/90 backdrop-blur md:block"
                style={{
                  transform: `translateY(${(1 - intra) * 10}px) scale(${0.96})`,
                  transition: "transform 220ms ease",
                }}
              >
                <div className="text-xs uppercase tracking-[0.2em] text-white/60">Next</div>
                <div className="mt-4 text-xl font-semibold">{slides[nextIdx].title}</div>
                <div className="mt-3 text-sm leading-relaxed text-white/70">{slides[nextIdx].body}</div>
              </div>
            </div>
          </div>

          <div className="pointer-events-none mt-8 flex items-center gap-2">
            {slides.map((_, i) => {
              const on = i === active;
              return (
                <div
                  key={i}
                  className={`h-1.5 flex-1 rounded-full ${on ? "bg-emerald-400" : "bg-white/15"}`}
                />
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}

function ScrollMarqueeBand({
  words,
  centerLabel = "StepCraft",
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
    <section ref={sectionRef} className="relative mt-14" style={{ height: "140vh" }}>
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
                Big typography + scroll-driven motion makes this feel like you’re traveling through “panels”, not just
                reading a page.
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

  const showcaseSlides: ShowcaseSlide[] = [
    {
      kicker: "Habit formation",
      title: "Daily logins feel rewarding.",
      body: "Players build streaks by hitting step goals, which increases the chance they return day after day. The phone becomes your “always-on” reminder.",
      cta: "Tell me more",
      imageUrl:
        "https://images.unsplash.com/photo-1520975958225-7f61d4308d7d?auto=format&fit=crop&w=2400&q=80",
    },
    {
      kicker: "Monetization",
      title: "More playtime, more purchases.",
      body: "Engaged players spend. StepCraft increases sessions and time online, raising conversion on ranks, keys, boosters, and cosmetic drops.",
      cta: "See examples",
      imageUrl:
        "https://images.unsplash.com/photo-1518548419970-58e3b4079ab2?auto=format&fit=crop&w=2400&q=80",
    },
    {
      kicker: "Direct contact",
      title: "A connection beyond Discord.",
      body: "Push notifications + app visibility give you a rare touchpoint even when the player is not online—keeping your server top-of-mind off-platform.",
      cta: "How it works",
      imageUrl:
        "https://images.unsplash.com/photo-1516542076529-1ea3854896f2?auto=format&fit=crop&w=2400&q=80",
    },
  ];

  return (
    <Layout>
      <SmoothScroll>
        <div className="bg-slate-950">
          <div className="mx-auto max-w-6xl px-4 py-10 md:px-6 md:py-14">
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
                      to={isAuthenticated ? "/dashboard" : "/account/register"}
                      className="flex h-11 items-center justify-center rounded-md bg-emerald-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                    >
                      {isAuthenticated ? "Go to dashboard" : "Create your account"}
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
                    Replace with a real screenshot montage (home → claim → dashboard).
                  </div>
                </div>
              </div>
            </section>

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

            <div className="mt-14">
              <StickyShowcase slides={showcaseSlides} themeClassName="" />
            </div>

            <ScrollMarqueeBand
              words={[
                "Retention",
                "Streaks",
                "Daily Goals",
                "Playtime",
                "Revenue",
                "Notifications",
                "Habit Loop",
                "Server Growth",
              ]}
              centerLabel="StepCraft pitch"
            />

            <section className="relative mt-16">
              <div className="rounded-3xl border border-slate-800/70 bg-slate-950/90 px-6 py-10 md:px-10">
                <div className="flex flex-wrap items-center justify-between gap-4">
                  <div>
                    <h2 className="text-2xl font-semibold text-white">Key ideas to pitch</h2>
                    <p className="mt-2 text-sm text-slate-400">
                      This is your “stacked cards” moment—nice for a contained interactive element.
                    </p>
                  </div>
                  <div className="rounded-full border border-slate-800 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-400">
                    Scroll to explore
                  </div>
                </div>

                <div className="mt-6 h-[560px]">
                  <ScrollStack className="h-full" useWindowScroll>
                    <ScrollStackItem itemClassName="bg-slate-950/70 border border-emerald-900/40">
                      <div className="text-sm uppercase tracking-[0.2em] text-emerald-300/70">Habit formation</div>
                      <h3 className="mt-3 text-2xl font-semibold text-white">Daily logins feel rewarding.</h3>
                      <p className="mt-3 text-sm text-slate-300">
                        Players build streaks by hitting step goals, which increases the chance they return day after day.
                      </p>
                      <div className="mt-6 rounded-xl border border-slate-800/70 bg-slate-900/30 p-4 text-xs text-slate-400">
                        Image placeholder: streak reward UI or daily notification.
                      </div>
                    </ScrollStackItem>

                    <ScrollStackItem itemClassName="bg-slate-950/70 border border-emerald-900/40">
                      <div className="text-sm uppercase tracking-[0.2em] text-emerald-300/70">Monetization</div>
                      <h3 className="mt-3 text-2xl font-semibold text-white">More playtime, more purchases.</h3>
                      <p className="mt-3 text-sm text-slate-300">
                        Reward loops increase engagement, which leads to higher conversion on ranks, keys, and limited drops.
                      </p>
                      <div className="mt-6 rounded-xl border border-slate-800/70 bg-slate-900/30 p-4 text-xs text-slate-400">
                        Image placeholder: store items, rank tiers, or marketplace UI.
                      </div>
                    </ScrollStackItem>

                    <ScrollStackItem itemClassName="bg-slate-950/70 border border-emerald-900/40">
                      <div className="text-sm uppercase tracking-[0.2em] text-emerald-300/70">Direct contact</div>
                      <h3 className="mt-3 text-2xl font-semibold text-white">A connection beyond Discord.</h3>
                      <p className="mt-3 text-sm text-slate-300">
                        Push notifications and app visibility give you a rare touchpoint, even when the player is not online.
                      </p>
                      <div className="mt-6 rounded-xl border border-slate-800/70 bg-slate-900/30 p-4 text-xs text-slate-400">
                        Image placeholder: push notification preview or app badge.
                      </div>
                    </ScrollStackItem>
                  </ScrollStack>
                </div>
              </div>
            </section>

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
                  Replace these blocks with real screenshots: mobile app, reward tiers, server dashboard.
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

            <section className="mt-16 rounded-2xl border border-slate-800 bg-slate-950/70 p-8 text-center">
              <h2 className="text-2xl font-semibold text-white">Bring retention and revenue together.</h2>
              <p className="mt-2 text-sm text-slate-400">
                Start your StepCraft server and build a community that logs in every day.
              </p>
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
        </div>
      </SmoothScroll>
    </Layout>
  );
}
