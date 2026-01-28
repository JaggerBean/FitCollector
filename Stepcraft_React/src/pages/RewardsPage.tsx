import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Layout } from "../components/Layout";
import { useAuthContext } from "../app/AuthContext";
import { getRewards, updateRewards, useDefaultRewards } from "../api/servers";
import type { RewardsTier } from "../api/types";

interface EditableTier {
  min_steps: number;
  label: string;
  rewardsText: string;
}

function toEditable(tier: RewardsTier): EditableTier {
  return {
    min_steps: tier.min_steps,
    label: tier.label,
    rewardsText: tier.rewards.join("\n"),
  };
}

function toPayload(tier: EditableTier): RewardsTier {
  return {
    min_steps: tier.min_steps,
    label: tier.label.trim(),
    rewards: tier.rewardsText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean),
  };
}

export default function RewardsPage() {
  const { serverName } = useParams();
  const { token } = useAuthContext();
  const [tiers, setTiers] = useState<EditableTier[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [isDefault, setIsDefault] = useState(false);

  const decodedName = useMemo(() => (serverName ? decodeURIComponent(serverName) : ""), [serverName]);

  useEffect(() => {
    if (!token || !decodedName) return;
    setLoading(true);
    getRewards(token, decodedName)
      .then((data) => {
        setTiers(data.tiers.map(toEditable));
        setIsDefault(Boolean(data.is_default));
      })
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  }, [token, decodedName]);

  const isValid = useMemo(() => {
    return tiers.length > 0 && tiers.every((tier) => tier.label.trim() && Number.isFinite(tier.min_steps));
  }, [tiers]);

  const onSave = async () => {
    if (!token || !decodedName) return;
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      const payload = tiers.map(toPayload);
      const updated = await updateRewards(token, decodedName, payload);
      setTiers(updated.tiers.map(toEditable));
      setIsDefault(Boolean(updated.is_default));
      setSuccess("Rewards updated.");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const onUseDefault = async () => {
    if (!token || !decodedName) return;
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      const updated = await useDefaultRewards(token, decodedName);
      setTiers(updated.tiers.map(toEditable));
      setIsDefault(Boolean(updated.is_default));
      setSuccess("Default rewards applied.");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const addTier = () => {
    setTiers((prev) => [...prev, { min_steps: 0, label: "", rewardsText: "" }]);
  };

  const updateTier = (index: number, patch: Partial<EditableTier>) => {
    setTiers((prev) => prev.map((tier, idx) => (idx === index ? { ...tier, ...patch } : tier)));
  };

  const removeTier = (index: number) => {
    setTiers((prev) => prev.filter((_, idx) => idx !== index));
  };

  return (
    <Layout title="Rewards">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Reward tiers</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            {decodedName} {isDefault ? "· default" : ""}
          </p>
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
      {success && (
        <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700 dark:border-emerald-700/40 dark:bg-emerald-900/20 dark:text-emerald-200">
          {success}
        </div>
      )}
      <div className="mt-6 space-y-4">
        {tiers.map((tier, index) => (
          <div
            key={`${tier.label}-${index}`}
            className="rounded-2xl border border-emerald-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900"
          >
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Tier {index + 1}</h2>
              <button
                type="button"
                onClick={() => removeTier(index)}
                className="text-sm font-medium text-red-600 hover:text-red-700"
              >
                Remove
              </button>
            </div>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Min steps</label>
                <input
                  type="number"
                  min={0}
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={tier.min_steps}
                  onChange={(event) => updateTier(index, { min_steps: Number(event.target.value) })}
                />
              </div>
              <div>
                <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Label</label>
                <input
                  className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                  value={tier.label}
                  onChange={(event) => updateTier(index, { label: event.target.value })}
                />
              </div>
            </div>
            <div className="mt-4">
              <label className="text-sm font-medium text-slate-700 dark:text-slate-300">Reward commands</label>
              <textarea
                className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                rows={4}
                placeholder="give {player} minecraft:diamond 1"
                value={tier.rewardsText}
                onChange={(event) => updateTier(index, { rewardsText: event.target.value })}
              />
              <p className="mt-1 text-xs text-slate-400">One command per line.</p>
            </div>
          </div>
        ))}
      </div>
      <div className="mt-6 flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={addTier}
          className="rounded-lg border border-emerald-200 bg-white px-4 py-2 text-sm font-medium text-emerald-700 shadow-sm hover:border-emerald-300 hover:bg-emerald-50 dark:border-slate-700 dark:bg-slate-900 dark:text-emerald-200"
        >
          Add tier
        </button>
        <button
          type="button"
          onClick={onSave}
          disabled={!isValid || loading}
          className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-emerald-700 disabled:opacity-70"
        >
          Save rewards
        </button>
        <button
          type="button"
          onClick={onUseDefault}
          disabled={loading}
          className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 shadow-sm hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
        >
          Use default
        </button>
      </div>
      {!isValid && (
        <div className="mt-2 text-sm text-red-600">Fill in a label for every tier before saving.</div>
      )}
    </Layout>
  );
}
