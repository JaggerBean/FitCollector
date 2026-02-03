import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import ScrollStack, { ScrollStackItem } from "../components/ScrollStack";
import { useAuthContext } from "../app/AuthContext";

export default function WhyJoinPage() {
  const { isAuthenticated } = useAuthContext();

  return (
    <Layout>
      <div className="rounded-3xl bg-slate-950/95 px-6 py-14 md:px-10">
        <section className="grid gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
          <div>
            <p className="text-xs uppercase tracking-[0.28em] text-emerald-300/80">Why you should join</p>
            <h1 className="mt-4 text-[2.4rem] font-semibold leading-tight text-white md:text-[3.2rem]">
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
          <div className="rounded-2xl border border-slate-800/70 bg-slate-950/70 p-6 shadow-[0_10px_30px_rgba(15,23,42,0.35)]">
            <div className="text-xs uppercase tracking-[0.2em] text-slate-400">Elevator pitch</div>
            <p className="mt-4 text-base leading-relaxed text-slate-200">
              StepCraft gives your server an always-on connection to players through the phone app they already use every
              day. By turning steps into rewards, you create daily streaks, higher retention, and more time online-which
              directly raises revenue from ranks, boosts, and cosmetics.
            </p>
            <div className="mt-6 rounded-xl border border-slate-800/70 bg-slate-900/40 p-4 text-xs text-slate-400">
              Image placeholder: app home screen, reward claim screen, or server dashboard.
            </div>
          </div>
        </section>

        <section className="mt-16 grid gap-6 lg:grid-cols-3">
          {[
            {
              title: "Retention loop",
              body:
                "Daily step goals give players a reason to check in even when they are not thinking about the server.",
            },
            {
              title: "Revenue lift",
              body:
                "More sessions and longer playtime amplify rank upgrades, cosmetics, and other monetized perks.",
            },
            {
              title: "Always-on contact",
              body:
                "Few Minecraft mods live on a player's phone. StepCraft keeps your server top-of-mind off-platform.",
            },
          ].map((item) => (
            <div
              key={item.title}
              className="rounded-2xl border border-slate-800/60 bg-slate-950/60 p-6 text-slate-200"
            >
              <div className="text-xs uppercase tracking-[0.2em] text-emerald-300/70">{item.title}</div>
              <p className="mt-3 text-sm leading-relaxed text-slate-300">{item.body}</p>
            </div>
          ))}
        </section>

        <section className="mt-16">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h2 className="text-2xl font-semibold text-white">Key ideas to pitch</h2>
              <p className="mt-2 text-sm text-slate-400">
                Use these talking points when explaining StepCraft to server owners.
              </p>
            </div>
            <div className="rounded-full border border-slate-800 px-4 py-2 text-xs uppercase tracking-[0.3em] text-slate-400">
              Scroll to explore
            </div>
          </div>

          <div className="mt-8 rounded-3xl border border-slate-900/70 bg-slate-950/60 p-6">
            <ScrollStack className="h-[70vh]" useWindowScroll={false}>
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
        </section>

        <section className="mt-16 grid gap-6 lg:grid-cols-[1.1fr_0.9fr] lg:items-center">
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
    </Layout>
  );
}
