import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { QRCodeCanvas } from "qrcode.react";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";
import { getOwnedServers, reopenServer } from "../api/servers";
import type { ServerSummary } from "../api/types";

export default function DashboardPage() {
  const { token } = useAuthContext();
  const [servers, setServers] = useState<ServerSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [reopenKey, setReopenKey] = useState<{ server: string; key: string } | null>(null);
  const [reopenLoading, setReopenLoading] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    getOwnedServers(token)
      .then((data) => setServers(data.servers))
      .catch((err) => setError((err as Error).message));
  }, [token]);

  const activeServers = servers.filter((server) => server.is_active !== false && server.is_deleted !== true);
  const deletedServers = servers.filter((server) => server.is_active === false || server.is_deleted === true);

  const onReopenServer = async (serverName: string) => {
    if (!token) return;
    const shouldContinue = window.confirm(
      `Re-open ${serverName}? A new API key will be generated and must be updated in your server config.`,
    );
    if (!shouldContinue) return;
    setReopenLoading(serverName);
    setError(null);
    try {
      const response = await reopenServer(token, serverName);
      setReopenKey({ server: response.server_name, key: response.api_key });
      const refreshed = await getOwnedServers(token);
      setServers(refreshed.servers);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setReopenLoading(null);
    }
  };

  return (
    <Layout>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Your servers</h1>
        <Link
          to="/register"
          className="rounded-lg border border-emerald-200 bg-white px-4 py-2 text-sm font-medium text-emerald-700 shadow-sm hover:border-emerald-300 hover:bg-emerald-50 dark:border-slate-800 dark:bg-slate-900 dark:text-emerald-200"
        >
          Register server
        </Link>
      </div>
      {error && (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
          {error}
        </div>
      )}
      {reopenKey && (
        <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
          New API key for <strong>{reopenKey.server}</strong>: <span className="font-mono">{reopenKey.key}</span>
          <div className="mt-1 text-xs text-emerald-700/80 dark:text-emerald-200/80">
            Save this key now — it will not be shown again.
          </div>
        </div>
      )}

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {activeServers.map((server) => (
          <div
            key={server.server_name}
            className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900"
          >
            <div className="text-lg font-semibold text-slate-900 dark:text-slate-100">
              {server.server_name}
            </div>
            <div className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {server.server_address || "No address"} · {server.server_version || "Unknown"}
            </div>
            {server.is_private && server.invite_code && (
              <div className="mt-2">
                <div className="rounded-lg border border-emerald-100 bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
                  Private · Invite {server.invite_code}
                </div>
                <div className="mt-3 flex items-center gap-3">
                  <div className="rounded-lg bg-white p-2 shadow-sm">
                    <QRCodeCanvas value={server.invite_code} size={72} includeMargin />
                  </div>
                  <div className="text-xs text-slate-500 dark:text-slate-400">
                    Scan to add this private server.
                  </div>
                </div>
              </div>
            )}
            <div className="mt-4 flex gap-2">
              <Link
                to={`/servers/${encodeURIComponent(server.server_name)}`}
                className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700"
              >
                Manage
              </Link>
              <Link
                to={`/servers/${encodeURIComponent(server.server_name)}/rewards`}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
              >
                Rewards
              </Link>
            </div>
          </div>
        ))}
      </div>
      {deletedServers.length > 0 && (
        <div className="mt-10">
          <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Deleted servers</h2>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            These servers no longer have an active API key.
          </p>
          <div className="mt-4 grid gap-4 md:grid-cols-2">
            {deletedServers.map((server) => (
              <div
                key={server.server_name}
                className="rounded-2xl border border-red-200 bg-white p-5 shadow-sm dark:border-red-900/40 dark:bg-slate-900"
              >
                <div className="text-lg font-semibold text-slate-900 dark:text-slate-100">
                  {server.server_name}
                </div>
                <div className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                  {server.server_address || "No address"} · {server.server_version || "Unknown"}
                </div>
                <div className="mt-2 inline-flex items-center rounded-full bg-red-50 px-3 py-1 text-xs font-semibold text-red-700 dark:bg-red-900/30 dark:text-red-200">
                  Deleted
                </div>
                <div className="mt-4">
                  <button
                    type="button"
                    onClick={() => onReopenServer(server.server_name)}
                    disabled={reopenLoading === server.server_name}
                    className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-700 hover:border-emerald-300 hover:bg-emerald-100 disabled:opacity-70 dark:border-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-200"
                  >
                    {reopenLoading === server.server_name ? "Re-opening..." : "Re-open server"}
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </Layout>
  );
}
