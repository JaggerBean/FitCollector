import { Link } from "react-router-dom";
import { Layout } from "../components/Layout";

export default function NotFoundPage() {
  return (
    <Layout title="Not found">
      <div className="rounded-3xl border border-emerald-100 bg-white p-8 text-center shadow-xl dark:border-slate-800 dark:bg-slate-900">
        <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Page not found</h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          The page you are looking for does not exist.
        </p>
        <Link
          to="/"
          className="mt-6 inline-flex rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-emerald-700"
        >
          Go to dashboard
        </Link>
      </div>
    </Layout>
  );
}
