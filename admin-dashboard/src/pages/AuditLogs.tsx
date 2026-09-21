import { useEffect, useState } from "react";
import { apiRequest } from "../api/client";
import type { AuditLog } from "../types";
import { formatDate } from "../utils/format";

const ACTION_FILTERS = [
  "",
  "USER_APPROVED",
  "USER_REJECTED",
  "USER_STATUS_CHANGED",
  "QUOTA_CHANGED",
  "ADMIN_SETTINGS_CHANGED",
  "ADMIN_FILE_METADATA_VIEWED",
  "ADMIN_FILE_DOWNLOADED",
  "ADMIN_FILE_TRASHED",
  "ADMIN_FILE_RESTORED",
  "ADMIN_FILE_PERMANENTLY_DELETED",
];

export default function AuditLogs() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [total, setTotal] = useState(0);
  const [skip, setSkip] = useState(0);
  const [action, setAction] = useState("");
  const [userId, setUserId] = useState("");
  const [search, setSearch] = useState("");
  const [error, setError] = useState("");
  const limit = 50;

  async function load(nextSkip = skip) {
    const params = new URLSearchParams();
    params.set("skip", String(nextSkip));
    params.set("limit", String(limit));
    if (action) params.set("action", action);
    if (userId.trim()) params.set("user_id", userId.trim());
    if (search.trim()) params.set("search", search.trim());
    const data = await apiRequest<{ logs: AuditLog[]; total: number }>(
      `/admin/audit-logs?${params.toString()}`,
    );
    setLogs(data.logs);
    setTotal(data.total);
    setSkip(nextSkip);
  }

  useEffect(() => {
    load(0).catch((err) => setError(String(err.message || err)));
  }, []);

  function applyFilters() {
    load(0).catch((err) => setError(String(err.message || err)));
  }

  const page = Math.floor(skip / limit) + 1;
  const totalPages = Math.max(1, Math.ceil(total / limit));

  return (
    <div className="page">
      <header className="page-header">
        <h1>Audit Logs</h1>
        <p>Immutable record of administrator actions and file access</p>
      </header>

      <div className="toolbar">
        <select className="input" value={action} onChange={(e) => setAction(e.target.value)}>
          {ACTION_FILTERS.map((value) => (
            <option key={value || "all"} value={value}>
              {value || "All actions"}
            </option>
          ))}
        </select>
        <input
          className="input input-narrow"
          placeholder="Target user ID"
          value={userId}
          onChange={(e) => setUserId(e.target.value.replace(/\D/g, ""))}
        />
        <input
          className="input"
          placeholder="Search action or reason"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button className="btn btn-primary" type="button" onClick={applyFilters}>
          Apply
        </button>
      </div>

      {error ? <div className="alert alert-error">{error}</div> : null}

      <section className="panel">
        <p className="table-meta">
          Showing {logs.length} of {total} entries (page {page} of {totalPages})
        </p>
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>Action</th>
              <th>Admin</th>
              <th>User</th>
              <th>File</th>
              <th>Reason</th>
              <th>Details</th>
              <th>IP</th>
            </tr>
          </thead>
          <tbody>
            {logs.length === 0 ? (
              <tr>
                <td colSpan={8}>No audit entries match your filters</td>
              </tr>
            ) : (
              logs.map((log) => (
                <tr key={log.id}>
                  <td>{formatDate(log.created_at)}</td>
                  <td>
                    <code>{log.action}</code>
                  </td>
                  <td>{log.admin_id ?? "—"}</td>
                  <td>{log.user_id ?? "—"}</td>
                  <td>{log.file_id ?? "—"}</td>
                  <td>{log.reason ?? "—"}</td>
                  <td className="details-cell">{log.details ?? "—"}</td>
                  <td>{log.ip_address ?? "—"}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        <div className="pagination">
          <button
            className="btn btn-secondary"
            type="button"
            disabled={skip <= 0}
            onClick={() => load(Math.max(0, skip - limit)).catch((err) => setError(String(err.message || err)))}
          >
            Previous
          </button>
          <button
            className="btn btn-secondary"
            type="button"
            disabled={skip + limit >= total}
            onClick={() => load(skip + limit).catch((err) => setError(String(err.message || err)))}
          >
            Next
          </button>
        </div>
      </section>
    </div>
  );
}
