import { useMemo } from "react";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import SmoothScroll from "../components/SmoothScroll";
import { PitchScrollScene } from "../components/ScrollStack";
import { DragGallery } from "../components/DraggableGallery";
import { useAuthContext } from "../app/AuthContext";
import casualRetentionImg from "../assets/MemoIMGs/Casual Player Retention.png";
import zeroFrictionImg from "../assets/MemoIMGs/Zero Friction.png";
import ownerControlImg from "../assets/MemoIMGs/Owner control.png";
import integrationsImg from "../assets/MemoIMGs/Integrations.png";
import reactivationImg from "../assets/MemoIMGs/Reactivation.png";

export default function WhyJoinPage() {
  const { isAuthenticated } = useAuthContext();

  const primaryCtaTo = isAuthenticated ? "/dashboard" : "/account/register";
  const primaryCtaLabel = isAuthenticated ? "Go to dashboard" : "Get started!";

  const scenes = useMemo(
    () => [
      {
        eyebrow: "Casual player retention",
        title: "Turn one-day visitors into regulars.",
        body: "Most players join, play once, and disappear, especially casual players. StepCraft gives them an easy daily reason to return: hit a step goal, claim a reward, feel progress. It turns \"I'll check it out\" into a routine that fits busy schedules.",
        imageAlt: "Daily step goal and reward claim",
        imageUrl: casualRetentionImg,
      },
      {
        eyebrow: "Zero friction",
        title: "Server-side only: players just join.",
        body: "No client mod. No extra installs. No \"go download this\" drop-off. Anyone who wants to join your server can participate immediately.",
        imageAlt: "Join server without installing anything",
        imageUrl: zeroFrictionImg,
      },
      {
        eyebrow: "Owner control",
        title: "Cater to casuals without nerfing the dedicated base.",
        body: "You control the reward structure: step thresholds, pacing, and payouts. Make it a small daily boost for casuals, without impacting your server's regular path of progression. Or go big and make a server dedicated to healthy lifestyles with exclusive ranks, cosmetics, and perks. The choice is yours.",
        imageAlt: "Reward tiers and tuning controls",
        imageUrl: ownerControlImg,
      },
      {
        eyebrow: "Integrations",
        title: "Command-driven rewards that work with your whole mod stack.",
        body: "Rewards are commands, so StepCraft can integrate with whatever mods you already run. Economy, ranks, crates, quests, skills, cosmetics, custom items. If it has a command, StepCraft can trigger it.",
        imageAlt: "Reward commands integrating with other plugins/mods",
        imageUrl: integrationsImg,
      },
      {
        eyebrow: "Reactivation",
        title: "Bring players back without spamming Discord.",
        body: "The phone app becomes a daily touchpoint: streak reminders, missed goals, and nudges that pull players back into Minecraft. It's reactivation that feels like a reward, not an announcement.",
        imageAlt: "App reminders and reactivation prompts",
        imageUrl: reactivationImg,
      },
    ],
    [],
  );

  return (
    <Layout>
      <SmoothScroll>
        <div className="bg-slate-950">
          <div className="mx-auto max-w-6xl px-4 py-8 sm:py-10 md:px-6 md:py-14">
            {/* HERO */}
            <section className="relative overflow-hidden rounded-3xl border border-slate-800/70 bg-slate-950/95 px-5 py-10 sm:px-6 sm:py-12 md:px-10 md:py-14">
              <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(16,185,129,0.14),transparent_55%)]" />

              <div className="relative grid gap-8 lg:grid-cols-[1.1fr_0.9fr] lg:items-center sm:gap-10">
                <div>
                  <p className="text-xs uppercase tracking-[0.22em] text-emerald-300/80 sm:tracking-[0.28em]">
                    Why you should choose StepCraft for your server
                  </p>

                  <h1 className="mt-4 text-[2.1rem] font-semibold leading-tight text-white sm:text-[2.6rem] md:text-[3.4rem]">
                    A retention system players can't ignore because it lives on their phone.
                  </h1>

                  {/* <p className="mt-5 text-base leading-relaxed text-slate-300 md:text-lg">
                    StepCraft turns real-world movement into in-game progress. Players build streaks, chase daily goals,
                    and come back more often. For server owners, that means higher retention, longer sessions, and more
                    opportunities to monetize without feeling pay-to-win.
                  </p> */}

                  <div className="mt-6 flex flex-wrap gap-3 sm:mt-8">
                    <Link
                      to={primaryCtaTo}
                      className="flex h-11 w-full items-center justify-center rounded-md bg-emerald-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300 sm:w-auto"
                    >
                      {primaryCtaLabel}
                    </Link>

                    {/* <Link
                      to="/"
                      className="flex h-11 items-center justify-center rounded-md border border-slate-700 px-6 text-sm font-semibold text-white transition hover:border-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300"
                    >
                      Back to landing
                    </Link> */}
                  </div>

                  <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                    <div className="rounded-xl border border-slate-800/70 bg-slate-950/55 p-4">
                      <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Retention</div>
                      <div className="mt-1 text-sm font-semibold text-white">More consecutive days played</div>
                      <div className="mt-1 text-xs text-slate-400">Fewer players forgetting to log in.</div>
                    </div>
                    <div className="rounded-xl border border-slate-800/70 bg-slate-950/55 p-4">
                      <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Marketability</div>
                      <div className="mt-1 text-sm font-semibold text-white">A unique health hook</div>
                      <div className="mt-1 text-xs text-slate-400">
                        Gives you a headline other servers can't copy: "walk IRL to earn in-game rewards."
                      </div>
                    </div>
                    <div className="rounded-xl border border-slate-800/70 bg-slate-950/55 p-4">
                      <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Visibility</div>
                      <div className="mt-1 text-sm font-semibold text-white">More touchpoints</div>
                      <div className="mt-1 text-xs text-slate-400">
                        Keeps your server in players' daily routine, not just when they're online.
                      </div>
                    </div>
                  </div>
                </div>

                <div className="rounded-2xl border border-slate-800/70 bg-slate-950/50 p-5 shadow-[0_10px_30px_rgba(15,23,42,0.35)] backdrop-blur sm:p-6">
                  <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Why it Works</div>

                  <p className="mt-4 text-sm leading-relaxed text-slate-200 sm:text-base">
                    StepCraft increases retention by giving players daily goals that reward real-world steps with in-game
                    progress. Unlike other daily server rewards, players feel like they earned these rewards and are more
                    likely to log on and claim them, which boosts playtime and raises conversion on in-game rank/item
                    sales. Unlike most mods, StepCraft also adds a phone app touchpoint, keeping your server top-of-mind
                    even when players aren't online.
                  </p>

                  <div className="mt-6 rounded-xl border border-slate-800/70 bg-slate-900/30 p-4 text-xs text-slate-300">
                    Tip: add 2-3 screenshots here (streaks, reward claim, server dashboard) and this becomes a shareable
                    one-pager for owners.
                  </div>
                </div>
              </div>
            </section>

            {/* KEY BENEFITS */}
            <section className="mt-8 grid gap-4 sm:mt-10 sm:grid-cols-2 lg:grid-cols-3">
              {[
                {
                  title: "Privacy & invite codes",
                  body: "Run private servers with invite codes + QR join, or go public. You stay in control of who can register.",
                },
                {
                  title: "Automated reward tiers",
                  body: "Set step thresholds and the exact commands to run at each tier. Tweak rewards anytime without the difficulty of config files.",
                },
                {
                  title: "Owner dashboard tools",
                  body: "Schedule push notifications that get sent to your players' devices, manage players (ban/wipe), and review an audit log so you can run ops without guesswork.",
                },
              ].map((item) => (
                <div
                  key={item.title}
                  className="rounded-2xl border border-slate-800/60 bg-slate-950/70 p-5 text-slate-200 sm:p-6"
                >
                  <div className="text-xs uppercase tracking-[0.2em] text-emerald-300/70">{item.title}</div>
                  <p className="mt-3 text-sm leading-relaxed text-slate-300">{item.body}</p>
                </div>
              ))}
            </section>

            {/* PINNED SCROLL STORY */}
            <div className="mt-8 sm:mt-12">
              <PitchScrollScene scenes={scenes} />
            </div>

            {/* DRAG GALLERY (SELLS "REAL PRODUCT", NOT PLACEHOLDER) */}
            <div className="mt-10 rounded-3xl border border-slate-800/70 bg-slate-950/70 p-5 sm:mt-14 sm:p-8">
              <div className="flex flex-wrap items-end justify-between gap-6">
                <div>
                  <div className="text-xs uppercase tracking-[0.28em] text-emerald-300/80">The player experience</div>
                  <h2 className="mt-3 text-xl font-semibold text-white sm:text-2xl">Clear goals, fast rewards, no clutter.</h2>
                  <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-300">
                    Players open the app and immediately see what to do next. It's built like a fitness app: a simple
                    daily target, a reward to claim, and a streak to protect, no menus to learn.
                  </p>
                </div>
              </div>

              <div className="mt-8">
                <DragGallery
                  items={[
                    { label: "Daily steps card" },
                    { label: "Rewards inbox" },
                    { label: "Streak tracker" },
                    { label: "Invite / QR join" },
                    { label: "Sync history" },
                  ]}
                />
              </div>

              <div className="mt-6 grid gap-3 sm:grid-cols-2 md:grid-cols-3">
                {[
                  {
                    h: "Instant feedback",
                    p: "Progress updates the moment steps sync, so players see the payoff right away.",
                  },
                  {
                    h: "Clear next step",
                    p: "The UI always answers \"what should I do next?\" with one obvious action.",
                  },
                  {
                    h: "Low cognitive load",
                    p: "It feels like a fitness app, not a mod menu, easy for casual players to adopt.",
                  },
                ].map((x) => (
                  <div key={x.h} className="rounded-2xl border border-slate-800/60 bg-slate-950/60 p-5">
                    <div className="text-sm font-semibold text-white">{x.h}</div>
                    <div className="mt-2 text-sm text-slate-300">{x.p}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* FINAL CTA */}
            <section className="mt-12 rounded-2xl border border-slate-800 bg-slate-950/70 p-6 text-center sm:mt-16 sm:p-8">
              <h2 className="text-xl font-semibold text-white sm:text-2xl">Turn daily movement into daily logins.</h2>
              <p className="mt-2 text-sm text-slate-400">
                If you want higher retention, more sessions, and a new off-platform touchpoint, StepCraft is built for
                you.
              </p>
              <div className="mt-6 flex justify-center">
                <Link
                  to={primaryCtaTo}
                  className="flex h-11 w-full items-center justify-center rounded-md bg-emerald-500 px-6 text-sm font-semibold text-slate-950 transition hover:bg-emerald-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-300 sm:w-auto"
                >
                  {primaryCtaLabel}
                </Link>
              </div>
              <div className="mt-4 text-xs text-slate-500">
                Want a quick owner demo? Add 2-3 screenshots above and share this page as your pitch.
              </div>
            </section>
          </div>
        </div>
      </SmoothScroll>
    </Layout>
  );
}
