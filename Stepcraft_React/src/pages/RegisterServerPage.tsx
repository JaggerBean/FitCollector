import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Layout } from "../components/Layout";
import Lanyard from "../components/Lanyard";
import { useAuthContext } from "../app/AuthContext";
import { getAuthMe, registerServer } from "../api/servers";
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
  const [showApiKey, setShowApiKey] = useState(false);
  const [touched, setTouched] = useState({
    serverName: false,
    ownerName: false,
    ownerEmail: false,
    serverAddress: false,
    serverVersion: false,
  });
  const showError = (key: keyof typeof touched, value: string) => {
    return touched[key] || value.trim().length > 0;
  };

  const errors = useMemo(() => {
    const next: Record<string, string> = {};
    if (!serverName.trim()) next.serverName = "Server name is required.";
    else if (serverName.trim().length < 3) next.serverName = "Server name must be at least 3 characters.";

    if (!ownerName.trim()) next.ownerName = "Owner name is required.";
    else if (ownerName.trim().length < 2) next.ownerName = "Owner name must be at least 2 characters.";

    if (!ownerEmail.trim()) next.ownerEmail = "Owner email is required.";
    else if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(ownerEmail.trim())) {
      next.ownerEmail = "Enter a valid email address.";
    }

    if (!serverAddress.trim()) next.serverAddress = "Server address is required.";
    else if (serverAddress.trim().length < 3 || /\s/.test(serverAddress)) {
      next.serverAddress = "Enter a valid server address (no spaces).";
    }

    if (!serverVersion.trim()) next.serverVersion = "Server version is required.";
    else if (!/^\d+(\.\d+){1,3}$/.test(serverVersion.trim())) {
      next.serverVersion = "Use a version like 1.20.4.";
    }

    return next;
  }, [serverName, ownerName, ownerEmail, serverAddress, serverVersion]);

  const canSubmit = useMemo(() => {
    return Boolean(
      serverName.trim() &&
        ownerName.trim() &&
        ownerEmail.trim() &&
        serverAddress.trim() &&
        serverVersion.trim() &&
        Object.keys(errors).length === 0,
    );
  }, [serverName, ownerName, ownerEmail, serverAddress, serverVersion, errors]);

  useEffect(() => {
    if (!token) return;
    getAuthMe(token)
      .then((me) => {
        setOwnerName((current) => current || me.name);
        setOwnerEmail((current) => current || me.email);
      })
      .catch(() => {
        // Ignore autofill failures.
      });
  }, [token]);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!token) return;
    setTouched({
      serverName: true,
      ownerName: true,
      ownerEmail: true,
      serverAddress: true,
      serverVersion: true,
    });
    if (Object.keys(errors).length > 0) return;
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
          noValidate
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
                onChange={(event) => {
                  setServerName(event.target.value);
                  if (!touched.serverName) {
                    setTouched((prev) => ({ ...prev, serverName: true }));
                  }
                }}
                onBlur={() => setTouched((prev) => ({ ...prev, serverName: true }))}
                aria-invalid={showError("serverName", serverName) && !!errors.serverName}
                minLength={3}
                title="Server name must be at least 3 characters."
                required
              />
              {showError("serverName", serverName) && errors.serverName && (
                <div className="mt-1 text-xs text-red-600 dark:text-red-300">{errors.serverName}</div>
              )}
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Owner name</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={ownerName}
                  onChange={(event) => {
                    setOwnerName(event.target.value);
                    if (!touched.ownerName) {
                      setTouched((prev) => ({ ...prev, ownerName: true }));
                    }
                  }}
                  onBlur={() => setTouched((prev) => ({ ...prev, ownerName: true }))}
                  aria-invalid={showError("ownerName", ownerName) && !!errors.ownerName}
                  minLength={2}
                  title="Owner name must be at least 2 characters."
                  required
                />
                {showError("ownerName", ownerName) && errors.ownerName && (
                  <div className="mt-1 text-xs text-red-600 dark:text-red-300">{errors.ownerName}</div>
                )}
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Owner email</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  type="email"
                  value={ownerEmail}
                  onChange={(event) => {
                    setOwnerEmail(event.target.value);
                    if (!touched.ownerEmail) {
                      setTouched((prev) => ({ ...prev, ownerEmail: true }));
                    }
                  }}
                  onBlur={() => setTouched((prev) => ({ ...prev, ownerEmail: true }))}
                  aria-invalid={showError("ownerEmail", ownerEmail) && !!errors.ownerEmail}
                  required
                />
                {showError("ownerEmail", ownerEmail) && errors.ownerEmail && (
                  <div className="mt-1 text-xs text-red-600 dark:text-red-300">{errors.ownerEmail}</div>
                )}
              </div>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Server address</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={serverAddress}
                  onChange={(event) => {
                    setServerAddress(event.target.value);
                    if (!touched.serverAddress) {
                      setTouched((prev) => ({ ...prev, serverAddress: true }));
                    }
                  }}
                  placeholder="mc.example.com"
                  onBlur={() => setTouched((prev) => ({ ...prev, serverAddress: true }))}
                  aria-invalid={showError("serverAddress", serverAddress) && !!errors.serverAddress}
                  required
                />
                {showError("serverAddress", serverAddress) && errors.serverAddress && (
                  <div className="mt-1 text-xs text-red-600 dark:text-red-300">{errors.serverAddress}</div>
                )}
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Server version</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={serverVersion}
                  onChange={(event) => {
                    setServerVersion(event.target.value);
                    if (!touched.serverVersion) {
                      setTouched((prev) => ({ ...prev, serverVersion: true }));
                    }
                  }}
                  placeholder="1.20.4"
                  onBlur={() => setTouched((prev) => ({ ...prev, serverVersion: true }))}
                  aria-invalid={showError("serverVersion", serverVersion) && !!errors.serverVersion}
                  pattern="\\d+(\\.\\d+){1,3}"
                  title="Use a version like 1.20.4."
                  required
                />
                {showError("serverVersion", serverVersion) && errors.serverVersion && (
                  <div className="mt-1 text-xs text-red-600 dark:text-red-300">{errors.serverVersion}</div>
                )}
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
      {showResult && result && (
        <div className="fixed inset-0 z-50 bg-slate-950/70 backdrop-blur touch-none overscroll-contain">
          <div className="relative h-full w-full">
            <div className="absolute inset-0 flex items-start justify-center overflow-visible">
              <Lanyard
                cardData={{
                  serverName: result.server_name,
                  apiKey: result.api_key,
                  inviteCode: result.invite_code,
                  ownerEmail,
                }}
              />
            </div>
            <div className="absolute bottom-8 left-1/2 flex -translate-x-1/2 flex-wrap items-center justify-center gap-3">
              <button
                type="button"
                className="rounded-lg border border-emerald-500/60 bg-emerald-900/30 px-4 py-2 text-sm font-semibold text-emerald-100 hover:border-emerald-300"
                onClick={() => setShowApiKey(true)}
              >
                Reveal API key
              </button>
              <button
                type="button"
                className="rounded-lg border border-slate-700 bg-slate-900/80 px-4 py-2 text-sm font-semibold text-slate-100 hover:border-slate-500"
                onClick={() => {
                  setShowResult(false);
                  clearForm();
                }}
              >
                Register another server
              </button>
              <button
                type="button"
                className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-emerald-700"
                onClick={() => navigate("/dashboard")}
              >
                Take me to Dashboard
              </button>
            </div>
            {showApiKey && (
              <div className="absolute inset-0 z-10 flex items-center justify-center bg-slate-950/60 backdrop-blur-sm">
                <div className="w-[min(92vw,520px)] rounded-2xl border border-emerald-500/40 bg-slate-950/90 p-6 text-slate-100 shadow-2xl">
                  <div className="text-lg font-semibold">Your API key</div>
                  <p className="mt-2 text-sm text-slate-300">
                    This is the only time you will be able to view this key in the app. We also emailed it to you.
                  </p>
                  <div className="mt-4 rounded-lg border border-emerald-500/40 bg-slate-900 px-3 py-2 font-mono text-xs text-emerald-100">
                    {result.api_key}
                  </div>
                  <div className="mt-5 flex justify-end gap-2">
                    <button
                      type="button"
                      className="rounded-lg border border-slate-600 px-3 py-2 text-sm font-semibold text-slate-100 hover:border-slate-400"
                      onClick={() => setShowApiKey(false)}
                    >
                      Close
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </Layout>
  );
}
