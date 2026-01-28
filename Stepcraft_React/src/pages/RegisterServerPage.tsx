import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";
import { registerServer } from "../api/servers";
import type { RegisterServerResponse } from "../api/types";

export default function RegisterServerPage() {
  const { token } = useAuthContext();
  const [serverName, setServerName] = useState("");
  const [ownerName, setOwnerName] = useState("");
  const [ownerEmail, setOwnerEmail] = useState("");
  const [serverAddress, setServerAddress] = useState("");
  const [serverVersion, setServerVersion] = useState("");
  const [isPrivate, setIsPrivate] = useState(false);
  const [inviteCode, setInviteCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RegisterServerResponse | null>(null);

  const canSubmit = useMemo(() => {
    return Boolean(serverName.trim() && ownerName.trim() && ownerEmail.trim());
  }, [serverName, ownerName, ownerEmail]);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    setError(null);
    setLoading(true);
    try {
      const payload = {
        server_name: serverName.trim(),
        owner_name: ownerName.trim(),
        owner_email: ownerEmail.trim(),
        server_address: serverAddress.trim() || undefined,
        server_version: serverVersion.trim() || undefined,
        is_private: isPrivate,
        invite_code: isPrivate ? inviteCode.trim() || undefined : undefined,
      };
      const response = await registerServer(token, payload);
      setResult(response);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout title="Register server">
      <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <form
          onSubmit={onSubmit}
          className="rounded-3xl border border-emerald-100 bg-white p-8 shadow-xl dark:border-slate-800 dark:bg-slate-900"
        >
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Register a server</h1>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
            Create a new StepCraft server and save the API key you receive.
          </p>
          {error && (
            <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900/40 dark:bg-red-900/20 dark:text-red-200">
              {error}
            </div>
          )}
          <div className="mt-6 grid gap-4">
            <div>
              <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Server name</label>
              <input
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                value={serverName}
                onChange={(event) => setServerName(event.target.value)}
                required
              />
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Owner name</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={ownerName}
                  onChange={(event) => setOwnerName(event.target.value)}
                  required
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Owner email</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  type="email"
                  value={ownerEmail}
                  onChange={(event) => setOwnerEmail(event.target.value)}
                  required
                />
              </div>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Server address</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={serverAddress}
                  onChange={(event) => setServerAddress(event.target.value)}
                  placeholder="mc.example.com"
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Server version</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={serverVersion}
                  onChange={(event) => setServerVersion(event.target.value)}
                  placeholder="1.20.4"
                />
              </div>
            </div>
            <div className="rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
              <label className="flex items-center gap-2 font-medium">
                <input
                  type="checkbox"
                  className="h-4 w-4 rounded border-slate-300 text-emerald-600"
                  checked={isPrivate}
                  onChange={(event) => setIsPrivate(event.target.checked)}
                />
                Make this server private
              </label>
              <p className="mt-1 text-xs text-emerald-700/80 dark:text-emerald-200/80">
                Private servers require an invite code to join.
              </p>
              {isPrivate && (
                <div className="mt-3">
                  <label className="text-xs font-medium text-emerald-800 dark:text-emerald-200">
                    Invite code (optional)
                  </label>
                  <input
                    className="mt-1 w-full rounded-lg border border-emerald-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-emerald-700 dark:bg-slate-950 dark:text-slate-100"
                    value={inviteCode}
                    onChange={(event) => setInviteCode(event.target.value)}
                    placeholder="Leave blank to auto-generate"
                  />
                </div>
              )}
            </div>
          </div>
          <button
            type="submit"
            disabled={!canSubmit || loading}
            className="mt-6 w-full rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-emerald-700 disabled:opacity-70"
          >
            {loading ? "Registering..." : "Register server"}
          </button>
        </form>
        <div className="rounded-3xl border border-slate-200 bg-white p-8 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Registration result</h2>
          {result ? (
            <div className="mt-4 space-y-3 text-sm text-slate-700 dark:text-slate-200">
              <div>
                <div className="text-xs uppercase text-slate-400">Server</div>
                <div className="font-semibold text-slate-900 dark:text-slate-100">{result.server_name}</div>
              </div>
              <div>
                <div className="text-xs uppercase text-slate-400">API key</div>
                <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 font-mono text-xs text-slate-900 dark:border-slate-700 dark:bg-slate-950 dark:text-emerald-200">
                  {result.api_key}
                </div>
                <div className="mt-1 text-xs text-slate-400">Save this key securely. You will not see it again.</div>
              </div>
              {result.invite_code && (
                <div>
                  <div className="text-xs uppercase text-slate-400">Invite code</div>
                  <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 font-mono text-xs text-emerald-700 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
                    {result.invite_code}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              Submit the form to generate your server API key.
            </p>
          )}
        </div>
      </div>
    </Layout>
  );
}
