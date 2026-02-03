import { useEffect, useMemo, useState } from "react";
import { Layout } from "../components/Layout";
import AnimatedList from "../components/AnimatedList";
import ConfirmDialog from "../components/ConfirmDialog";
import { useAuthContext } from "../app/AuthContext";
import { getAuditLog, getOwnedServers } from "../api/servers";
import type { AuditLogResponse, AuditEvent, OwnedServersResponse } from "../api/types";

export default function AuditLogPage() {
  const { token } = useAuthContext();
  const [servers, setServers] = useState<OwnedServersResponse["servers"]>([]);
  const [items, setItems] = useState<AuditEvent[]>([]);
  const [serverFilter, setServerFilter] = useState("all");
  const [actionFilter, setActionFilter] = useState("");
  const [limit, setLimit] = useState(200);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeAudit, setActiveAudit] = useState<AuditEvent | null>(null);
  const [actionOptions, setActionOptions] = useState<string[]>([]);

  useEffect(() => {
    if (!token) return;
    getOwnedServers(token)
      .then((data) => setServers(data.servers))
      .catch((err) => setError((err as Error).message));
  }, [token]);

  useEffect(() => {
    if (!token) return;
    setLoading(true);
    setError(null);
    const serverParam = serverFilter === "all" ? undefined : serverFilter;
    const baseRequest = getAuditLog(token, { server: serverParam, limit });
    const filteredRequest = actionFilter.trim()
      ? getAuditLog(token, { server: serverParam, action: actionFilter.trim(), limit })
      : baseRequest;
    Promise.all([baseRequest, filteredRequest])
      .then(([baseData, filteredData]) => {
        setItems(filteredData.items);
        const options = new Set(baseData.items.map((item) => item.action));
        setActionOptions(Array.from(options).sort());
      })
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }, [token, serverFilter, actionFilter, limit]);

  const actionTags = useMemo(() => actionOptions, [actionOptions]);

  return (
    <Layout>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Audit log</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Track admin actions like push sends, reward edits, bans, wipes, and server changes.
          </p>
        </div>
      </div>

      <div className="mt-6 rounded-2xl border border-slate-800/60 bg-slate-950/40 p-4">
        <div className="grid gap-3 md:grid-cols-[1.2fr_1fr_0.6fr]">
          <label className="text-xs font-semibold uppercase tracking-wide text-slate-400">
            Server
            <select
              className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
              value={serverFilter}
              onChange={(event) => setServerFilter(event.target.value)}
            >
              <option value="all">All servers</option>
              {servers.map((server) => (
                <option key={server.server_name} value={server.server_name}>
                  {server.server_name}
                </option>
              ))}
            </select>
          </label>
          <label className="text-xs font-semibold uppercase tracking-wide text-slate-400">
            Action
            <select
              className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
              value={actionFilter || "all"}
              onChange={(event) => setActionFilter(event.target.value === "all" ? "" : event.target.value)}
            >
              <option value="all">All actions</option>
              {actionTags.map((action) => (
                <option key={action} value={action}>
                  {action}
                </option>
              ))}
            </select>
          </label>
          <label className="text-xs font-semibold uppercase tracking-wide text-slate-400">
            Limit
            <input
              type="number"
              min={50}
              max={1000}
              className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100"
              value={limit}
              onChange={(event) => setLimit(Number(event.target.value))}
            />
          </label>
        </div>
      </div>

      {error && (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
          {error}
        </div>
      )}

      {loading ? (
        <div className="mt-6 text-sm text-slate-500 dark:text-slate-400">Loading audit events...</div>
      ) : (
        <div className="mt-6 space-y-4">
          {items.length === 0 ? (
            <div className="rounded-xl border border-slate-800/70 bg-slate-950/40 px-4 py-8 text-center text-sm text-slate-400">
              No audit events yet.
            </div>
          ) : (
            <AnimatedList
              items={items}
              className="text-sm text-slate-300"
              maxHeightClassName="max-h-[520px]"
              onItemSelect={(item) => setActiveAudit(item)}
              renderItem={(item) => (
                <div className="rounded-2xl border border-slate-800/70 bg-slate-950/40 p-4 shadow-sm">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <div className="text-sm font-semibold text-slate-100">
                        {item.summary || item.action.replace(/_/g, " ")}
                      </div>
                      <div className="mt-1 text-xs text-slate-400">
                        {item.server_name} · {new Date(item.created_at).toLocaleString()}
                      </div>
                    </div>
                    <div className="text-xs font-medium text-emerald-300">
                      {item.actor_email || "System"}
                    </div>
                  </div>
                </div>
              )}
            />
          )}
        </div>
      )}
      <ConfirmDialog
        open={!!activeAudit}
        title={activeAudit?.summary || activeAudit?.action?.replace(/_/g, " ") || "Audit entry"}
        confirmLabel="Close"
        cancelLabel="Close"
        onConfirm={() => setActiveAudit(null)}
        onCancel={() => setActiveAudit(null)}
        content={
          activeAudit ? (
            <div className="space-y-3">
              <div className="text-xs text-slate-500 dark:text-slate-400">
                <div>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">Server:</span>{" "}
                  {activeAudit.server_name}
                </div>
                <div>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">Action:</span>{" "}
                  {activeAudit.action}
                </div>
                <div>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">Actor:</span>{" "}
                  {activeAudit.actor_email || "System"}
                </div>
                <div>
                  <span className="font-semibold text-slate-700 dark:text-slate-200">When:</span>{" "}
                  {new Date(activeAudit.created_at).toLocaleString()}
                </div>
              </div>
              <div>
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Details
                </div>
                <pre className="mt-2 max-h-72 overflow-auto rounded-lg border border-slate-800 bg-slate-950/60 px-3 py-2 text-xs text-slate-300">
                  {JSON.stringify(activeAudit.details ?? {}, null, 2)}
                </pre>
              </div>
            </div>
          ) : null
        }
      />
    </Layout>
  );
}
