import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import ConfirmDialog from "../components/ConfirmDialog";
import Lanyard from "../components/Lanyard";
import { useAuthContext } from "../app/AuthContext";
import { registerServer } from "../api/servers";
import type { RegisterServerResponse } from "../api/types";

export default function RegisterServerPage() {
  const { token } = useAuthContext();
  const navigate = useNavigate();
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
  const [showResult, setShowResult] = useState(false);

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
      setShowResult(true);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const clearForm = () => {
    setServerName("");
    setOwnerName("");
    setOwnerEmail("");
    setServerAddress("");
    setServerVersion("");
    setIsPrivate(false);
    setInviteCode("");
    setResult(null);
  };

  return (
    <Layout>
      <div className="mx-auto w-full max-w-4xl">
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
      </div>
      <ConfirmDialog
        open={showResult && !!result}
        title="Server registered"
        panelClassName="w-[min(96vw,1100px)] max-w-none"
        contentClassName="text-slate-100"
        content={
          result && (
            <Lanyard
              cardContent={
                <div className="space-y-2">
                  <div className="text-[11px] uppercase tracking-wide text-emerald-200/80">Server</div>
                  <div className="text-sm font-semibold text-white">{result.server_name}</div>
                  <div className="pt-1 text-[11px] uppercase tracking-wide text-emerald-200/80">API key</div>
                  <div className="rounded-md border border-emerald-300/40 bg-emerald-950/60 px-2 py-1 font-mono text-[10px] text-emerald-100">
                    {result.api_key}
                  </div>
                  {result.invite_code && (
                    <>
                      <div className="pt-1 text-[11px] uppercase tracking-wide text-emerald-200/80">Invite code</div>
                      <div className="rounded-md border border-emerald-300/40 bg-emerald-950/60 px-2 py-1 font-mono text-[10px] text-emerald-100">
                        {result.invite_code}
                      </div>
                    </>
                  )}
                  <div className="pt-2 text-[11px] leading-snug text-emerald-50/80">
                    Add this key to your StepCraft Minecraft mod configuration on the server to sync steps and rewards.
                  </div>
                  <div className="text-[11px] leading-snug text-emerald-50/70">
                    An email has been sent to {ownerEmail}. If you don’t see it, check spam.
                  </div>
                </div>
              }
            />
          )
        }
        confirmLabel="Take me to Dashboard"
        cancelLabel="Register another server"
        onConfirm={() => navigate("/dashboard")}
        onCancel={() => {
          setShowResult(false);
          clearForm();
        }}
      />
    </Layout>
  );
}
