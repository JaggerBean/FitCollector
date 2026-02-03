import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { QRCodeCanvas } from "qrcode.react";
import { Layout } from "../components/Layout";
import ConfirmDialog from "../components/ConfirmDialog";
import { useAuthContext } from "../app/AuthContext";
import { deleteServer, getOwnedServers, pauseServer, reopenServer, resumeServer } from "../api/servers";
import type { ServerSummary } from "../api/types";

type ConfirmState = {
  message: string;
  title?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: "danger" | "default";
  resolve: (value: boolean) => void;
};

export default function DashboardPage() {
  const { token } = useAuthContext();
  const [servers, setServers] = useState<ServerSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [reopenKey, setReopenKey] = useState<{ server: string; key: string } | null>(null);
  const [reopenLoading, setReopenLoading] = useState<string | null>(null);
  const [pauseLoading, setPauseLoading] = useState<string | null>(null);
  const [resumeLoading, setResumeLoading] = useState<string | null>(null);
  const [deleteLoading, setDeleteLoading] = useState<string | null>(null);
  const [confirmState, setConfirmState] = useState<ConfirmState | null>(null);

  useEffect(() => {
    if (!token) return;
    getOwnedServers(token)
      .then((data) => setServers(data.servers))
      .catch((err) => setError((err as Error).message));
  }, [token]);

  const activeServers = servers.filter((server) => server.is_active !== false && server.is_deleted !== true);
  const deletedServers = servers.filter((server) => server.is_active === false || server.is_deleted === true);

  const requestConfirm = (message: string, options: Omit<ConfirmState, "message" | "resolve"> = {}) =>
    new Promise<boolean>((resolve) => {
      setConfirmState({ message, resolve, ...options });
    });

  const closeConfirm = (result: boolean) => {
    if (!confirmState) return;
    confirmState.resolve(result);
    setConfirmState(null);
  };

  const onReopenServer = async (serverName: string) => {
    if (!token) return;
    const shouldContinue = await requestConfirm(
      `Re-open ${serverName}? This issues a NEW server API key and invalidates the old one. You must update your server config immediately or services will fail.`,
      { title: "Re-open server", confirmLabel: "Re-open", tone: "danger" },
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

  const onPauseServer = async (serverName: string) => {
    if (!token) return;
    const shouldContinue = await requestConfirm(
      `Pause ${serverName}? This disables the API key so players cannot sync until you resume.`,
      { title: "Pause server", confirmLabel: "Pause server" },
    );
    if (!shouldContinue) return;
    setPauseLoading(serverName);
    setError(null);
    try {
      await pauseServer(token, serverName);
      const refreshed = await getOwnedServers(token);
      setServers(refreshed.servers);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setPauseLoading(null);
    }
  };

  const onResumeServer = async (serverName: string) => {
    if (!token) return;
    const shouldContinue = await requestConfirm(
      `Resume ${serverName}? This re-activates the last API key so players can sync again.`,
      { title: "Resume server", confirmLabel: "Resume server" },
    );
    if (!shouldContinue) return;
    setResumeLoading(serverName);
    setError(null);
    try {
      await resumeServer(token, serverName);
      const refreshed = await getOwnedServers(token);
      setServers(refreshed.servers);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setResumeLoading(null);
    }
  };

  const onDeleteServer = async (serverName: string) => {
    if (!token) return;
    const shouldContinue = await requestConfirm(
      `Permanently delete ${serverName}? This removes ALL data (players, steps, rewards, push history, and API keys). This cannot be undone.`,
      { title: "Delete server", confirmLabel: "Delete permanently", tone: "danger" },
    );
    if (!shouldContinue) return;
    setDeleteLoading(serverName);
    setError(null);
    try {
      await deleteServer(token, serverName);
      const refreshed = await getOwnedServers(token);
      setServers(refreshed.servers);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setDeleteLoading(null);
    }
  };

  return (
    <Layout>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Your servers</h1>
        <div className="flex flex-wrap items-center gap-2">
          <Link
            to="/audit"
            className="rounded-lg border border-slate-700 bg-slate-950/60 px-4 py-2 text-sm font-medium text-slate-100 shadow-sm transition hover:border-emerald-500/70 hover:bg-emerald-600 hover:text-white"
          >
            Audit log
          </Link>
          <Link
            to="/register"
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-emerald-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
          >
            Register server
          </Link>
        </div>
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
            <div className="mt-4 flex flex-wrap gap-2">
              <Link
                to={`/servers/${encodeURIComponent(server.server_name)}`}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
              >
                Manage
              </Link>
              <Link
                to={`/servers/${encodeURIComponent(server.server_name)}/rewards`}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
              >
                Rewards
              </Link>
              <Link
                to={`/servers/${encodeURIComponent(server.server_name)}/push`}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
              >
                Push notifications
              </Link>
              <button
                type="button"
                onClick={() => onPauseServer(server.server_name)}
                disabled={pauseLoading === server.server_name}
                className="rounded-lg border border-amber-200 px-3 py-2 text-sm font-medium text-amber-700 hover:border-amber-300 disabled:opacity-70 dark:border-amber-500/40 dark:text-amber-200"
              >
                {pauseLoading === server.server_name ? "Pausing..." : "Pause server"}
              </button>
              <button
                type="button"
                onClick={() => onDeleteServer(server.server_name)}
                disabled={deleteLoading === server.server_name}
                className="rounded-lg border border-red-200 px-3 py-2 text-sm font-medium text-red-700 hover:border-red-300 disabled:opacity-70 dark:border-red-500/40 dark:text-red-200"
              >
                {deleteLoading === server.server_name ? "Deleting..." : "Delete server"}
              </button>
            </div>
          </div>
        ))}
      </div>
      {deletedServers.length > 0 && (
        <div className="mt-10">
          <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Inactive servers</h2>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            These servers are paused. You can resume them or issue a new API key.
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
                  Inactive
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  <button
                    type="button"
                    onClick={() => onResumeServer(server.server_name)}
                    disabled={resumeLoading === server.server_name}
                    className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-700 transition hover:border-emerald-600 hover:bg-emerald-600 hover:text-white disabled:opacity-70 dark:border-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-200 dark:hover:border-emerald-500 dark:hover:bg-emerald-600"
                  >
                    {resumeLoading === server.server_name ? "Resuming..." : "Resume server"}
                  </button>
                  <button
                    type="button"
                    onClick={() => onReopenServer(server.server_name)}
                    disabled={reopenLoading === server.server_name}
                    className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-700 transition hover:border-emerald-600 hover:bg-emerald-600 hover:text-white disabled:opacity-70 dark:border-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-200 dark:hover:border-emerald-500 dark:hover:bg-emerald-600"
                  >
                    {reopenLoading === server.server_name ? "Re-opening..." : "Re-open server"}
                  </button>
                  <button
                    type="button"
                    onClick={() => onDeleteServer(server.server_name)}
                    disabled={deleteLoading === server.server_name}
                    className="rounded-lg border border-red-200 px-3 py-2 text-sm font-medium text-red-700 hover:border-red-300 disabled:opacity-70 dark:border-red-500/40 dark:text-red-200"
                  >
                    {deleteLoading === server.server_name ? "Deleting..." : "Delete server"}
                  </button>
                </div>
              </div>
            ))}
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
