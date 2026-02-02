import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";
import { createPush, listPush } from "../api/servers";
import type { PushItem } from "../api/types";

function toLocalDateTime(date: Date) {
  const pad = (num: number) => String(num).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(
    date.getMinutes(),
  )}`;
}

type TimezoneOption = {
  value: string;
  label: string;
  offsetMinutes: number;
};

const COMMON_TIMEZONES = [
  "UTC",
  "America/New_York",
  "America/Chicago",
  "America/Denver",
  "America/Los_Angeles",
  "Europe/London",
  "Europe/Paris",
  "Europe/Berlin",
  "Europe/Warsaw",
  "Asia/Tokyo",
  "Australia/Sydney",
];

const PREFERRED_BY_ABBR: Record<string, string> = {
  UTC: "UTC",
  EST: "America/New_York",
  EDT: "America/New_York",
  CST: "America/Chicago",
  CDT: "America/Chicago",
  MST: "America/Denver",
  MDT: "America/Denver",
  PST: "America/Los_Angeles",
  PDT: "America/Los_Angeles",
  AKST: "America/Anchorage",
  AKDT: "America/Anchorage",
  HST: "Pacific/Honolulu",
  GMT: "Europe/London",
  BST: "Europe/London",
  CET: "Europe/Paris",
  CEST: "Europe/Paris",
  JST: "Asia/Tokyo",
  AEST: "Australia/Sydney",
  AEDT: "Australia/Sydney",
};

function getTimezoneAbbr(tz: string): string {
  const now = new Date();
  try {
    const parts = new Intl.DateTimeFormat("en-US", {
      timeZone: tz,
      timeZoneName: "short",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).formatToParts(now);
    return parts.find((part) => part.type === "timeZoneName")?.value ?? "UTC";
  } catch {
    return "UTC";
  }
}

function getTimezoneOffsetMinutes(tz: string): number {
  try {
    const now = new Date();
    const asText = now.toLocaleString("en-US", { timeZone: tz });
    const asDate = new Date(asText);
    return Math.round((asDate.getTime() - now.getTime()) / 60000);
  } catch {
    return 0;
  }
}

function canonicalTimezone(tz: string): string {
  try {
    return new Intl.DateTimeFormat("en-US", { timeZone: tz }).resolvedOptions().timeZone;
  } catch {
    return tz;
  }
}

function scoreTimezoneCandidate(tz: string, abbr: string): number {
  if (PREFERRED_BY_ABBR[abbr] === tz) return 1000;
  const commonRank = COMMON_TIMEZONES.indexOf(tz);
  if (commonRank >= 0) return 500 - commonRank;
  const depth = tz.split("/").length;
  return 100 - depth;
}

function buildTimezoneOptions(): TimezoneOption[] {
  const supported = typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("timeZone") : [];
  const canonicalCandidates: string[] = [];
  const seenCanonical = new Set<string>();

  [...COMMON_TIMEZONES, ...supported].forEach((tz) => {
    const canonical = canonicalTimezone(tz);
    if (seenCanonical.has(canonical)) return;
    seenCanonical.add(canonical);
    canonicalCandidates.push(canonical);
  });

  // Deduplicate by abbreviation (EST/CST/etc.) and keep only the best-known zone for each.
  const bestByAbbr = new Map<string, string>();
  canonicalCandidates.forEach((tz) => {
    const abbr = getTimezoneAbbr(tz);
    const current = bestByAbbr.get(abbr);
    if (!current) {
      bestByAbbr.set(abbr, tz);
      return;
    }
    if (scoreTimezoneCandidate(tz, abbr) > scoreTimezoneCandidate(current, abbr)) {
      bestByAbbr.set(abbr, tz);
    }
  });

  const options: TimezoneOption[] = Array.from(bestByAbbr.entries()).map(([abbr, tz]) => {
    const offsetMinutes = getTimezoneOffsetMinutes(tz);
    return {
      value: tz,
      label: `${tz} (${abbr})`,
      offsetMinutes,
    };
  });

  return options.sort((a, b) => {
    if (a.offsetMinutes !== b.offsetMinutes) return a.offsetMinutes - b.offsetMinutes;
    return a.label.localeCompare(b.label);
  });
}

export default function PushPage() {
  const { serverName } = useParams();
  const { token } = useAuthContext();
  const [items, setItems] = useState<PushItem[]>([]);
  const [message, setMessage] = useState("");
  const [scheduledAt, setScheduledAt] = useState(toLocalDateTime(new Date()));
  const [timezone, setTimezone] = useState(
    Intl?.DateTimeFormat?.().resolvedOptions().timeZone || "UTC",
  );
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const decodedName = useMemo(() => (serverName ? decodeURIComponent(serverName) : ""), [serverName]);
  const timezoneOptions = useMemo(() => buildTimezoneOptions(), []);

  useEffect(() => {
    if (!timezoneOptions.length) return;
    if (!timezoneOptions.some((option) => option.value === timezone)) {
      setTimezone(timezoneOptions[0].value);
    }
  }, [timezone, timezoneOptions]);

  useEffect(() => {
    if (!token || !decodedName) return;
    listPush(token, decodedName)
      .then((data) => setItems(data.items))
      .catch((err) => setError((err as Error).message));
  }, [token, decodedName]);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!token || !decodedName) return;
    if (!scheduledAt) {
      setError("Pick a schedule time.");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const response = await createPush(token, decodedName, {
        message,
        scheduled_at: scheduledAt,
        timezone,
      });
      if (response.item) {
        setItems((prev) => [response.item, ...prev]);
      } else {
        const refreshed = await listPush(token, decodedName);
        setItems(refreshed.items);
      }
      setMessage("");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Push notifications</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{decodedName}</p>
        </div>
        <Link
          to={`/servers/${encodeURIComponent(decodedName)}`}
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
      <form
        onSubmit={onSubmit}
        className="mt-6 rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900"
      >
        <div>
          <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Message</label>
          <textarea
            className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
            rows={3}
            maxLength={240}
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            required
          />
          <p className="mt-1 text-xs text-slate-400">One message per day. Max 240 characters.</p>
        </div>
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <div>
            <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Schedule time</label>
            <input
              type="datetime-local"
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              value={scheduledAt}
              onChange={(event) => setScheduledAt(event.target.value)}
              required
            />
          </div>
          <div>
            <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Timezone</label>
            <select
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              value={timezone}
              onChange={(event) => setTimezone(event.target.value)}
              required
            >
              {timezoneOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        <button
          type="submit"
          disabled={loading}
          className="mt-4 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-emerald-700 disabled:opacity-70"
        >
          {loading ? "Scheduling..." : "Schedule notification"}
        </button>
      </form>
      <div className="mt-6 rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Scheduled</h2>
        <div className="mt-4 space-y-3 text-sm text-slate-700 dark:text-slate-200">
          {items.length ? (
            items.filter(Boolean).map((item) => (
              <div key={item.id} className="rounded-lg border border-slate-200 px-3 py-2 dark:border-slate-700">
                <div className="font-medium text-slate-900 dark:text-slate-100">{item.message}</div>
                <div className="mt-1 text-xs text-slate-400">Scheduled: {item.scheduled_at}</div>
              </div>
            ))
          ) : (
            <div className="text-sm text-slate-500 dark:text-slate-400">No scheduled notifications yet.</div>
          )}
        </div>
      </div>
    </Layout>
  );
}
