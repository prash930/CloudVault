import { formatBytes, statusClass } from "../utils/format";

export default function StatusBadge({ status }: { status: string }) {
  return <span className={`badge ${statusClass(status)}`}>{status}</span>;
}

export function StorageBar({ used, quota }: { used: number; quota: number }) {
  const pct = quota > 0 ? Math.min(100, (used / quota) * 100) : 0;
  const over = used > quota;
  return (
    <div className="storage-bar-wrap">
      <div className="storage-bar-label">
        <span>{formatBytes(used)} / {formatBytes(quota)}</span>
        <span>{pct.toFixed(1)}%</span>
      </div>
      <div className="storage-bar">
        <div className={`storage-bar-fill ${over ? "over" : ""}`} style={{ width: `${pct}%` }} />
      </div>
      {over ? <small className="warning-text">Usage exceeds quota — new uploads blocked</small> : null}
    </div>
  );
}
