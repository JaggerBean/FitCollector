import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { QRCodeCanvas } from "qrcode.react";
import { Layout } from "../components/Layout";
import ConfirmDialog from "../components/ConfirmDialog";
import AnimatedList from "../components/AnimatedList";
import { useAuthContext } from "../app/AuthContext";
import {
  banPlayer,
  claimReward,
  getClaimStatus,
  getClaimWindow,
  getOwnedServers,
  getInactivePruneSettings,
  getRewards,
  getServerInfo,
  getDaySteps,
  listBans,
  listPlayers,
  runInactivePrune,
  togglePrivacy,
  unbanPlayer,
  updateInactivePruneSettings,
  updateClaimWindow,
  wipePlayer,
} from "../api/servers";
import type {
  ClaimWindowResponse,
  InactivePruneSettingsResponse,
  InactivePruneRunResponse,
  PlayersListResponse,
  RewardsResponse,
  ServerInfo,
  ServerSummary,
} from "../api/types";

export default function ServerManagePage() {
  type ConfirmState = {
    message: string;
    title?: string;
    confirmLabel?: string;
    cancelLabel?: string;
    tone?: "danger" | "default";
    resolve: (value: boolean) => void;
  };

  const todayLocal = useMemo(() => {
    const now = new Date();
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
  }, []);

  const { serverName } = useParams();
  const { token } = useAuthContext();
  const [info, setInfo] = useState<ServerInfo | null>(null);
  const [meta, setMeta] = useState<ServerSummary | null>(null);
  const [players, setPlayers] = useState<PlayersListResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [privacyLoading, setPrivacyLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionOutput, setActionOutput] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [username, setUsername] = useState("");
  const [reason, setReason] = useState("");
  const [query, setQuery] = useState("");
  const [limit, setLimit] = useState(100);
  const [actionDay, setActionDay] = useState(todayLocal);
  const [selectedTierMinSteps, setSelectedTierMinSteps] = useState<number | null>(null);
  const [rewardTiers, setRewardTiers] = useState<RewardsResponse["tiers"]>([]);
  const [claimWindow, setClaimWindow] = useState<ClaimWindowResponse | null>(null);
  const [claimWindowSaving, setClaimWindowSaving] = useState(false);
  const [pruneSettings, setPruneSettings] = useState<InactivePruneSettingsResponse | null>(null);
  const [pruneSaving, setPruneSaving] = useState(false);
  const [pruneRunning, setPruneRunning] = useState(false);
  const [pruneError, setPruneError] = useState<string | null>(null);
  const [pruneOutput, setPruneOutput] = useState<InactivePruneRunResponse | null>(null);
  const [confirmState, setConfirmState] = useState<ConfirmState | null>(null);
  const serverToolsRef = useRef<HTMLDivElement | null>(null);
  const actionOutputRef = useRef<HTMLPreElement | null>(null);

  const usernameSuggestions = useMemo(
    () => players?.players?.map((player) => player.minecraft_username) ?? [],
    [players],
  );

  const filteredSuggestions = useMemo(() => {
    if (!username) return usernameSuggestions;
    const lower = username.toLowerCase();
    return usernameSuggestions
      .filter((name) => name.toLowerCase().startsWith(lower))
      .sort((a, b) => {
        const al = a.toLowerCase();
        const bl = b.toLowerCase();
        const aExact = al === lower;
        const bExact = bl === lower;
        if (aExact && !bExact) return -1;
        if (!aExact && bExact) return 1;
        if (a.length !== b.length) return a.length - b.length;
        return al.localeCompare(bl);
      });
  }, [username, usernameSuggestions]);

  const getUsernameSuggestion = (value: string) => {
    if (!value) return null;
    const lower = value.toLowerCase();
    return usernameSuggestions.find((name) => name.toLowerCase().startsWith(lower)) ?? null;
  };

  const onUsernameKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== "Tab") return;
    const suggestion = getUsernameSuggestion(username);
    if (suggestion && suggestion !== username) {
      event.preventDefault();
      setUsername(suggestion);
    }
  };

  const decodedName = useMemo(() => (serverName ? decodeURIComponent(serverName) : ""), [serverName]);

  useEffect(() => {
    if (!token || !decodedName) return;
    setLoading(true);
    setError(null);
    Promise.all([
      getServerInfo(token, decodedName),
      getOwnedServers(token),
      listPlayers(token, decodedName, 200, 0),
      getInactivePruneSettings(token, decodedName),
      getClaimWindow(token, decodedName),
      getRewards(token, decodedName),
    ])
      .then(([infoResponse, owned, playersResponse, pruneResponse, claimWindowResponse, rewardsResponse]) => {
        setInfo(infoResponse);
        setMeta(owned.servers.find((server) => server.server_name === decodedName) ?? null);
        setPlayers(playersResponse);
        setPruneSettings(pruneResponse);
        setClaimWindow(claimWindowResponse);
        setRewardTiers(rewardsResponse.tiers ?? []);
        const firstTier = rewardsResponse.tiers?.[0];
        if (firstTier) setSelectedTierMinSteps(firstTier.min_steps);
      })
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }, [token, decodedName]);

  const updatePruneSetting = (patch: Partial<InactivePruneSettingsResponse>) => {
    setPruneSettings((prev) => (prev ? { ...prev, ...patch } : prev));
  };

  const savePruneSettings = async () => {
    if (!token || !decodedName || !pruneSettings) return;
    setPruneSaving(true);
    setPruneError(null);
    try {
      const response = await updateInactivePruneSettings(token, decodedName, {
        enabled: pruneSettings.enabled,
        max_inactive_days: pruneSettings.enabled ? pruneSettings.max_inactive_days ?? 30 : null,
        mode: pruneSettings.mode,
      });
      setPruneSettings(response);
    } catch (err) {
      setPruneError((err as Error).message);
    } finally {
      setPruneSaving(false);
    }
  };

  const saveClaimWindow = async () => {
    if (!token || !decodedName || !claimWindow) return;
    setClaimWindowSaving(true);
    setError(null);
    try {
      const response = await updateClaimWindow(token, decodedName, claimWindow.claim_buffer_days);
      setClaimWindow(response);
      setInfo((prev) => (prev ? { ...prev, claim_buffer_days: response.claim_buffer_days } : prev));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setClaimWindowSaving(false);
    }
  };

  const requestConfirm = (message: string, options: Omit<ConfirmState, "message" | "resolve"> = {}) =>
    new Promise<boolean>((resolve) => {
      setConfirmState({ message, resolve, ...options });
    });

  const closeConfirm = (result: boolean) => {
    if (!confirmState) return;
    confirmState.resolve(result);
    setConfirmState(null);
  };

  const runPrune = async (dryRun: boolean) => {
    if (!token || !decodedName) return;
    if (!dryRun) {
      const shouldContinue = await requestConfirm(
        `Run inactive cleanup on ${decodedName}? ${pruneSettings?.mode === "wipe" ? "This permanently deletes player keys, step records, claims, and ban records for inactive players." : "This deactivates inactive players (they can re-register later), but does not delete their data."}`,
        { title: "Run cleanup", confirmLabel: "Run now", tone: "danger" },
      );
      if (!shouldContinue) return;
    }
    setPruneRunning(true);
    setPruneError(null);
    try {
      const response = await runInactivePrune(token, decodedName, dryRun);
      setPruneOutput(response);
    } catch (err) {
      setPruneError((err as Error).message);
    } finally {
      setPruneRunning(false);
    }
  };

  const onTogglePrivacy = async () => {
    if (!token || !decodedName || !info) return;
    const nextLabel = info.is_private ? "public" : "private";
    const shouldContinue = await requestConfirm(
      `Switch ${decodedName} to ${nextLabel}? ${nextLabel === "private" ? "New users will need an invite code to join." : "Anyone will be able to find and join this server."}`,
      { title: "Change privacy", confirmLabel: "Confirm" },
    );
    if (!shouldContinue) return;
    setPrivacyLoading(true);
    try {
      const response = await togglePrivacy(token, decodedName, !info.is_private);
      setInfo((prev) => (prev ? { ...prev, is_private: response.is_private, invite_code: response.invite_code } : prev));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setPrivacyLoading(false);
    }
  };

  const runAction = async (action: () => Promise<unknown>) => {
    setActionLoading(true);
    setActionError(null);
    setActionOutput(null);
    try {
      const result = await action();
      setActionOutput(JSON.stringify(result, null, 2));
      setTimeout(() => {
        actionOutputRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      }, 50);
    } catch (err) {
      setActionError((err as Error).message);
      setTimeout(() => {
        actionOutputRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      }, 50);
    } finally {
      setActionLoading(false);
    }
  };

  const confirmAndRunAction = async (message: string, action: () => Promise<unknown>) => {
    const shouldContinue = await requestConfirm(message, { title: "Confirm action", confirmLabel: "Continue" });
    if (!shouldContinue) return;
    await runAction(action);
  };

  return (
    <Layout>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">{decodedName}</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            {meta?.server_address || "No address"} · {meta?.server_version || "Unknown version"}
          </p>
        </div>
        <Link
          to="/dashboard"
          className="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:border-slate-300 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
        >
          Back
        </Link>
      </div>
      {error && (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
          {error}
        </div>
      )}
      {loading ? (
        <div className="mt-6 text-sm text-slate-500 dark:text-slate-400">Loading server data...</div>
      ) : (
        <div className="mt-6 grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="space-y-4">
            <div className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
              <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Server info</h2>
              <div className="mt-3 grid gap-2 text-sm text-slate-600 dark:text-slate-300">
                <div className="flex items-center justify-between">
                  <span>Max players</span>
                  <span className="font-medium text-slate-900 dark:text-slate-100">
                    {info?.max_players ?? "Unlimited"}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span>Current players</span>
                  <span className="font-medium text-slate-900 dark:text-slate-100">
                    {info?.current_players ?? 0}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span>Slots available</span>
                  <span className="font-medium text-slate-900 dark:text-slate-100">
                    {info?.slots_available ?? "Unlimited"}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span>Claim buffer days</span>
                  <span className="font-medium text-slate-900 dark:text-slate-100">
                    {info?.claim_buffer_days ?? claimWindow?.claim_buffer_days ?? 1}
                  </span>
                </div>
              </div>
            </div>
            <div className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
              <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Privacy</h2>
              <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
                Control who can register to your server.
              </p>
              <div className="mt-4 flex items-center gap-3">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  {info?.is_private ? "Private" : "Public"}
                </span>
                <button
                  type="button"
                  onClick={onTogglePrivacy}
                  disabled={privacyLoading}
                  className="rounded-lg border border-emerald-200 bg-white px-3 py-2 text-sm font-medium text-emerald-700 shadow-sm hover:border-emerald-300 hover:bg-emerald-50 disabled:opacity-70 dark:border-slate-700 dark:bg-slate-900 dark:text-emerald-200"
                >
                  {privacyLoading ? "Updating..." : info?.is_private ? "Make public" : "Make private"}
                </button>
              </div>
              {info?.is_private && info.invite_code && (
                <div className="mt-3">
                  <div className="rounded-lg border border-emerald-100 bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
                    Invite code: {info.invite_code}
                  </div>
                  <div className="mt-3 flex items-center gap-3">
                    <div className="rounded-lg bg-white p-2 shadow-sm">
                      <QRCodeCanvas value={info.invite_code} size={88} includeMargin />
                    </div>
                    <div className="text-xs text-slate-500 dark:text-slate-400">
                      Scan to add this private server.
                    </div>
                  </div>
                </div>
              )}
            </div>
            <div className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
              <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Claim window</h2>
              <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
                Allow claims for today and the past N days.
              </p>
              <div className="mt-4 flex flex-wrap items-end gap-3">
                <div>
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                    Buffer days
                  </label>
                  <input
                    type="number"
                    min={0}
                    className="mt-1 w-32 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                    value={claimWindow?.claim_buffer_days ?? 1}
                    onChange={(event) =>
                      setClaimWindow((prev) =>
                        prev ? { ...prev, claim_buffer_days: Number(event.target.value) } : prev,
                      )
                    }
                  />
                </div>
                <button
                  type="button"
                  onClick={saveClaimWindow}
                  disabled={claimWindowSaving}
                  className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-70"
                >
                  {claimWindowSaving ? "Saving..." : "Save"}
                </button>
              </div>
            </div>
            <div className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
              <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Inactive cleanup</h2>
              <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
                Remove players who haven’t claimed a reward within your threshold. If enabled this will automatically run daily as well.
              </p>
              <div className="mt-4 space-y-3 text-sm text-slate-700 dark:text-slate-200">
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={pruneSettings?.enabled ?? false}
                    onChange={(event) => updatePruneSetting({ enabled: event.target.checked })}
                  />
                  Enable auto cleanup
                </label>
                <div className="grid gap-3 sm:grid-cols-2">
                  <div>
                    <label className="text-sm font-medium text-slate-700 dark:text-slate-300">
                      Max inactive days
                    </label>
                    <input
                      type="number"
                      min={1}
                      className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                      value={pruneSettings?.max_inactive_days ?? ""}
                      onChange={(event) =>
                        updatePruneSetting({
                          max_inactive_days: event.target.value ? Number(event.target.value) : null,
                        })
                      }
                      disabled={!pruneSettings?.enabled}
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Mode</label>
                    <select
                      className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                      value={pruneSettings?.mode ?? "deactivate"}
                      onChange={(event) => updatePruneSetting({ mode: event.target.value as "deactivate" | "wipe" })}
                      disabled={!pruneSettings?.enabled}
                    >
                      <option value="deactivate">Deactivate (reversible)</option>
                      <option value="wipe">Wipe data (permanent)</option>
                    </select>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={savePruneSettings}
                    disabled={pruneSaving}
                    className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-70"
                  >
                    {pruneSaving ? "Saving..." : "Save settings"}
                  </button>
                  <button
                    type="button"
                    onClick={() => runPrune(true)}
                    disabled={pruneRunning || !pruneSettings?.enabled}
                    className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 disabled:opacity-70 dark:border-slate-700 dark:text-slate-200"
                  >
                    {pruneRunning ? "Running..." : "Dry run"}
                  </button>
                  <button
                    type="button"
                    onClick={() => runPrune(false)}
                    disabled={pruneRunning || !pruneSettings?.enabled}
                    className="rounded-lg border border-red-200 px-3 py-2 text-sm font-medium text-red-700 hover:border-red-300 disabled:opacity-70"
                  >
                    Run now
                  </button>
                </div>
                {pruneError && (
                  <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
                    {pruneError}
                  </div>
                )}
                {pruneOutput && (
                  <pre className="max-h-64 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200">
                    {JSON.stringify(pruneOutput, null, 2)}
                  </pre>
                )}
              </div>
            </div>
          </div>
          <div className="flex h-full flex-col rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Players</h2>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
              {players?.total_players ?? 0} registered players
            </p>
            {players?.players?.length ? (
              <AnimatedList
                items={players.players}
                className="mt-4 min-h-[16rem] flex-1 text-sm text-slate-700 dark:text-slate-200"
                maxHeightClassName="max-h-[420px]"
                onItemSelect={(player) => {
                  setUsername(player.minecraft_username);
                  serverToolsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
                }}
                renderItem={(player) => (
                  <div className="rounded-lg border border-slate-200 px-3 py-2 dark:border-slate-700">
                    <div className="flex items-center justify-between gap-3">
                      <span>{player.minecraft_username}</span>
                      <span className="text-xs text-slate-500 dark:text-slate-400">
                        {player.device_count} device{player.device_count === 1 ? "" : "s"}
                      </span>
                    </div>
                  </div>
                )}
              />
            ) : (
              <div className="mt-4 text-sm text-slate-500 dark:text-slate-400">No players yet.</div>
            )}
          </div>
          <div
            ref={serverToolsRef}
            className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900 lg:col-span-2"
          >
            <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Server tools</h2>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
              Run admin actions for this server.
            </p>
            <div className="mt-4 grid gap-4">
              <div className="rounded-2xl border border-slate-200/70 bg-slate-50/70 p-5 dark:border-slate-800 dark:bg-slate-950">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">Player actions</h3>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      Select a player once, then use the tools below.
                    </p>
                  </div>
                </div>
                <div className="mt-4 rounded-xl border border-slate-200/70 bg-white/70 p-4 dark:border-slate-800 dark:bg-slate-900/70">
                  <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    Player context
                  </h4>
                  <div className="mt-3">
                    <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Minecraft username</label>
                    <input
                      className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                      value={username}
                      onChange={(event) => setUsername(event.target.value)}
                      placeholder="Player name"
                      list="player-suggestions"
                      onKeyDown={onUsernameKeyDown}
                    />
                    <datalist id="player-suggestions">
                      {filteredSuggestions.map((name) => (
                        <option key={name} value={name} />
                      ))}
                    </datalist>
                  </div>
                </div>

                <div className="mt-4 grid gap-4 lg:grid-cols-3">
                  <div className="rounded-xl border border-slate-200/70 bg-white/70 p-4 dark:border-slate-800 dark:bg-slate-900/70">
                    <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                      Player lookup
                    </h4>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      Optional day for step checks.
                    </p>
                    <div className="mt-3">
                      <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Day</label>
                      <input
                        type="date"
                        className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                        value={actionDay}
                        onChange={(event) => setActionDay(event.target.value)}
                      />
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <button
                        type="button"
                        disabled={actionLoading || !username.trim()}
                        onClick={() =>
                          runAction(() =>
                            getDaySteps(token!, decodedName, username.trim(), actionDay.trim() || undefined),
                          )
                        }
                        className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                      >
                        Day steps
                      </button>
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200/70 bg-white/70 p-4 dark:border-slate-800 dark:bg-slate-900/70">
                    <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                      Claim tools
                    </h4>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      Check or mark a specific tier.
                    </p>
                    <div className="mt-3">
                      <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Reward tier</label>
                      <select
                        className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                        value={selectedTierMinSteps ?? ""}
                        onChange={(event) =>
                          setSelectedTierMinSteps(event.target.value ? Number(event.target.value) : null)
                        }
                      >
                        {rewardTiers.length === 0 && <option value="">No tiers loaded</option>}
                        {rewardTiers.map((tier) => (
                          <option key={tier.min_steps} value={tier.min_steps}>
                            {tier.label} · {tier.min_steps}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() =>
                          runAction(() =>
                            getClaimStatus(
                              token!,
                              decodedName,
                              username.trim(),
                              actionDay.trim() || undefined,
                              selectedTierMinSteps ?? undefined,
                            ),
                          )
                        }
                        className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                        disabled={actionLoading || !username.trim() || selectedTierMinSteps == null}
                      >
                        Claim status
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          runAction(() =>
                            claimReward(
                              token!,
                              decodedName,
                              username.trim(),
                              actionDay.trim() || undefined,
                              selectedTierMinSteps ?? undefined,
                            ),
                          )
                        }
                        className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-70"
                        disabled={actionLoading || !username.trim() || selectedTierMinSteps == null}
                      >
                        Mark claimed
                      </button>
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200/70 bg-white/70 p-4 dark:border-slate-800 dark:bg-slate-900/70">
                    <h4 className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                      Moderation
                    </h4>
                    <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                      Ban, unban, or wipe player data.
                    </p>
                    <div className="mt-3">
                      <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Ban reason</label>
                      <input
                        className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                        value={reason}
                        onChange={(event) => setReason(event.target.value)}
                        placeholder="broke code of conduct"
                      />
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <button
                        type="button"
                        disabled={actionLoading}
                        onClick={() => runAction(() => listBans(token!, decodedName))}
                        className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                      >
                        List bans
                      </button>
                      <button
                        type="button"
                        disabled={actionLoading || !username.trim()}
                        onClick={() =>
                          confirmAndRunAction(
                            `Ban ${username.trim()} on ${decodedName}? This bans the username AND all associated devices for this server until unbanned.`,
                            () => banPlayer(token!, decodedName, username.trim(), reason.trim() || "broke code of conduct"),
                          )
                        }
                        className="rounded-lg border border-red-200 px-3 py-2 text-sm font-medium text-red-700 hover:border-red-300"
                      >
                        Ban player
                      </button>
                      <button
                        type="button"
                        disabled={actionLoading || !username.trim()}
                        onClick={() =>
                          confirmAndRunAction(
                            `Unban ${username.trim()} on ${decodedName}? This removes the username ban and all associated device bans for this server.`,
                            () => unbanPlayer(token!, decodedName, username.trim()),
                          )
                        }
                        className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                      >
                        Unban player
                      </button>
                      <button
                        type="button"
                        disabled={actionLoading || !username.trim()}
                        onClick={() =>
                          confirmAndRunAction(
                            `Wipe ${username.trim()} on ${decodedName}? This permanently deletes player keys, step records, claims, and bans for this username on this server.`,
                            () => wipePlayer(token!, decodedName, username.trim()),
                          )
                        }
                        className="rounded-lg border border-red-200 px-3 py-2 text-sm font-medium text-red-700 hover:border-red-300"
                      >
                        Wipe player
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <div className="rounded-xl border border-slate-200/70 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-950">
                <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">Player list query</h3>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Fetch player registrations for quick reference.
                </p>
                <div className="mt-3 grid gap-4 md:grid-cols-2">
                  <div>
                    <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Search query</label>
                    <input
                      className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                      value={query}
                      onChange={(event) => setQuery(event.target.value)}
                      placeholder="Optional"
                    />
                  </div>
                  <div>
                    <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Limit</label>
                    <input
                      type="number"
                      min={1}
                      className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                      value={limit}
                      onChange={(event) => setLimit(Number(event.target.value))}
                    />
                  </div>
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  <button
                    type="button"
                    disabled={actionLoading}
                    onClick={() =>
                      runAction(() =>
                        listPlayers(token!, decodedName, limit, 0, query.trim() || undefined),
                      )
                    }
                    className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                  >
                    List players
                  </button>
                </div>
              </div>

              {actionError && (
                <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
                  {actionError}
                </div>
              )}
              {actionOutput && (
                <pre
                  ref={actionOutputRef}
                  className="max-h-64 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200"
                >
                  {actionOutput}
                </pre>
              )}
            </div>
          </div>
        </div>
      )}
      <ConfirmDialog
        open={!!confirmState}
        title={confirmState?.title}
        message={confirmState?.message ?? ""}
        confirmLabel={confirmState?.confirmLabel}
        cancelLabel={confirmState?.cancelLabel}
        tone={confirmState?.tone}
        onConfirm={() => closeConfirm(true)}
        onCancel={() => closeConfirm(false)}
      />
    </Layout>
  );
}
