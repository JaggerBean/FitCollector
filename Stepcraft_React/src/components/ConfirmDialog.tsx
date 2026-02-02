import { useEffect } from "react";

type ConfirmDialogProps = {
  open: boolean;
  title?: string;
  message?: string;
  content?: React.ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: "danger" | "default";
  onConfirm: () => void;
  onCancel: () => void;
};

export default function ConfirmDialog({
  open,
  title = "Confirm action",
  message,
  content,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  tone = "default",
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onCancel();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onCancel]);

  if (!open) return null;

  return (
    <div className="confirm-overlay" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
      <div className="confirm-panel">
        <div className="text-sm font-semibold text-slate-900 dark:text-slate-100" id="confirm-title">
          {title}
        </div>
        {content ? (
          <div className="mt-3 text-sm text-slate-600 dark:text-slate-300">{content}</div>
        ) : (
          <div className="mt-2 text-sm text-slate-600 dark:text-slate-300">{message}</div>
        )}
        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:border-slate-300 dark:border-slate-700 dark:text-slate-200"
            onClick={onCancel}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className={
              tone === "danger"
                ? "rounded-lg border border-red-200 bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-700"
                : "rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700"
            }
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
