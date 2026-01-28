import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";
import { getOwnedServers } from "../api/servers";
import type { ServerSummary } from "../api/types";

export default function DashboardPage() {
  const { token } = useAuthContext();
  const [servers, setServers] = useState<ServerSummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    getOwnedServers(token)
      .then((data) => setServers(data.servers))
      .catch((err) => setError((err as Error).message));
  }, [token]);

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
      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {servers.map((server) => (
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
              <div className="mt-2 rounded-lg border border-emerald-100 bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-700 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
                Private · Invite {server.invite_code}
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
    </Layout>
  );
}
