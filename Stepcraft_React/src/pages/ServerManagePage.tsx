import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";
import {
  banPlayer,
  claimReward,
  getClaimStatus,
  getOwnedServers,
  getServerInfo,
  getYesterdaySteps,
  listBans,
  listPlayers,
  togglePrivacy,
  unbanPlayer,
  wipePlayer,
} from "../api/servers";
import type { PlayersListResponse, ServerInfo, ServerSummary } from "../api/types";

export default function ServerManagePage() {
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
  const [offset, setOffset] = useState(0);

  const decodedName = useMemo(() => (serverName ? decodeURIComponent(serverName) : ""), [serverName]);

  useEffect(() => {
    if (!token || !decodedName) return;
    setLoading(true);
    setError(null);
    Promise.all([
      getServerInfo(token, decodedName),
      getOwnedServers(token),
      listPlayers(token, decodedName, 200, 0),
    ])
      .then(([infoResponse, owned, playersResponse]) => {
        setInfo(infoResponse);
        setMeta(owned.servers.find((server) => server.server_name === decodedName) ?? null);
        setPlayers(playersResponse);
      })
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }, [token, decodedName]);

  const onTogglePrivacy = async () => {
    if (!token || !decodedName || !info) return;
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
    } catch (err) {
      setActionError((err as Error).message);
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <Layout title="Manage server">
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
              <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Quick actions</h2>
              <div className="mt-3 flex flex-wrap gap-2">
                <Link
                  to={`/servers/${encodeURIComponent(decodedName)}/rewards`}
                  className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700"
                >
                  Rewards
                </Link>
                <Link
                  to={`/servers/${encodeURIComponent(decodedName)}/push`}
                  className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                >
                  Push notifications
                </Link>
              </div>
            </div>
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
                <div className="mt-3 rounded-lg border border-emerald-100 bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
                  Invite code: {info.invite_code}
                </div>
              )}
            </div>
          </div>
          <div className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Players</h2>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
              {players?.total_players ?? 0} registered players
            </p>
            <div className="mt-4 max-h-72 space-y-2 overflow-y-auto text-sm text-slate-700 dark:text-slate-200">
              {players?.players?.length ? (
                players.players.map((player) => (
                  <div
                    key={player.minecraft_username}
                    className="rounded-lg border border-slate-200 px-3 py-2 dark:border-slate-700"
                  >
                    {player.minecraft_username}
                  </div>
                ))
              ) : (
                <div className="text-sm text-slate-500 dark:text-slate-400">No players yet.</div>
              )}
            </div>
          </div>
          <div className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
            <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Server tools</h2>
            <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
              Run admin actions for this server.
            </p>
            <div className="mt-4 grid gap-4">
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Minecraft username</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  placeholder="Player name"
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Ban reason</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="broke code of conduct"
                />
              </div>
              <div className="grid gap-4 md:grid-cols-3">
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
                <div>
                  <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Offset</label>
                  <input
                    type="number"
                    min={0}
                    className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                    value={offset}
                    onChange={(event) => setOffset(Number(event.target.value))}
                  />
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={actionLoading}
                  onClick={() =>
                    runAction(() =>
                      listPlayers(token!, decodedName, limit, offset, query.trim() || undefined),
                    )
                  }
                  className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                >
                  List players
                </button>
                <button
                  type="button"
                  disabled={actionLoading || !username.trim()}
                  onClick={() => runAction(() => getYesterdaySteps(token!, decodedName, username.trim()))}
                  className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                >
                  Yesterday steps
                </button>
                <button
                  type="button"
                  disabled={actionLoading || !username.trim()}
                  onClick={() => runAction(() => getClaimStatus(token!, decodedName, username.trim()))}
                  className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                >
                  Claim status
                </button>
                <button
                  type="button"
                  disabled={actionLoading || !username.trim()}
                  onClick={() => runAction(() => claimReward(token!, decodedName, username.trim()))}
                  className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-70"
                >
                  Mark claimed
                </button>
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
                  onClick={() => runAction(() => banPlayer(token!, decodedName, username.trim(), reason.trim() || "broke code of conduct"))}
                  className="rounded-lg border border-red-200 px-3 py-2 text-sm font-medium text-red-700 hover:border-red-300"
                >
                  Ban player
                </button>
                <button
                  type="button"
                  disabled={actionLoading || !username.trim()}
                  onClick={() => runAction(() => unbanPlayer(token!, decodedName, username.trim()))}
                  className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
                >
                  Unban player
                </button>
                <button
                  type="button"
                  disabled={actionLoading || !username.trim()}
                  onClick={() => runAction(() => wipePlayer(token!, decodedName, username.trim()))}
                  className="rounded-lg border border-red-200 px-3 py-2 text-sm font-medium text-red-700 hover:border-red-300"
                >
                  Wipe player
                </button>
              </div>
              {actionError && (
                <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
                  {actionError}
                </div>
              )}
              {actionOutput && (
                <pre className="max-h-64 overflow-auto rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200">
                  {actionOutput}
                </pre>
              )}
            </div>
          </div>
        </div>
      )}
    </Layout>
  );
}
