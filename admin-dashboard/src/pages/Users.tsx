import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiRequest } from "../api/client";
import type { User } from "../types";
import StatusBadge, { StorageBar } from "../components/StatusBadge";
import { QUOTA_PRESETS, formatDate, parseQuotaInput } from "../utils/format";

export default function Users() {
  const [users, setUsers] = useState<User[]>([]);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [selected, setSelected] = useState<User | null>(null);
  const [reason, setReason] = useState("");
  const [quotaUnit, setQuotaUnit] = useState<"GB" | "TB">("GB");
  const [customQuota, setCustomQuota] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [showEmailCol, setShowEmailCol] = useState(true);
  const [resetTarget, setResetTarget] = useState<User | null>(null);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [resetError, setResetError] = useState("");

  async function loadUsers() {
    const params = new URLSearchParams();
    if (search.trim()) params.set("search", search.trim());
    if (statusFilter) params.set("status", statusFilter);
    const data = await apiRequest<{ users: User[] }>(`/admin/users?${params.toString()}`);
    setUsers(data.users);
  }

  useEffect(() => {
    loadUsers().catch((err) => setError(String(err.message || err)));
  }, []);

  async function selectUser(userId: number) {
    const detail = await apiRequest<User>(`/admin/users/${userId}`);
    setSelected(detail);
    setMessage("");
    setError("");
  }

  async function runQuotaUpdate(bytes: number) {
    if (!selected) return;
    const result = await apiRequest<{ warning?: string; message: string }>(
      `/admin/users/${selected.id}/update-quota`,
      { method: "POST", body: JSON.stringify({ quota_bytes: bytes, reason: reason || undefined }) },
    );
    setMessage(result.warning ? `${result.message}` : result.message);
    await loadUsers();
    await selectUser(selected.id);
  }

  function openResetModal(user: User) {
    setResetTarget(user);
    setNewPassword("");
    setConfirmPassword("");
    setResetError("");
  }

  async function runResetPassword() {
    if (!resetTarget) return;
    if (newPassword.length < 8) {
      setResetError("Password must be at least 8 characters.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setResetError("Passwords do not match.");
      return;
    }
    try {
      await apiRequest<{ message: string }>(`/admin/users/${resetTarget.id}/reset-password`, {
        method: "POST",
        body: JSON.stringify({ new_password: newPassword }),
      });
      setMessage(`Password reset for ${resetTarget.email}`);
      setResetTarget(null);
      setError("");
    } catch (err) {
      setResetError(String((err as Error).message || err));
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Users</h1>
        <p>Search, review, and manage registered users</p>
      </header>

      <div className="toolbar" style={{ flexWrap: "wrap", gap: "0.75rem", alignItems: "center" }}>
        <input className="input" placeholder="Search name or email" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select className="input" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All statuses</option>
          {["PENDING", "ACTIVE", "WARNED", "SUSPENDED", "BANNED"].map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
        <button className="btn btn-primary" type="button" onClick={() => loadUsers()}>Search</button>

        <div style={{ display: "flex", gap: "1rem", marginLeft: "auto", fontSize: "0.85rem", alignItems: "center" }}>
          <span><strong>Columns:</strong></span>
          <label style={{ display: "inline-flex", alignItems: "center", gap: "0.3rem", cursor: "pointer" }}>
            <input type="checkbox" checked={showEmailCol} onChange={(e) => setShowEmailCol(e.target.checked)} />
            Email
          </label>
        </div>
      </div>

      {error ? <div className="alert alert-error">{error}</div> : null}
      {message ? <div className="alert alert-success">{message}</div> : null}

      <div className="split-view">
        <section className="panel">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                {showEmailCol && <th>Email</th>}
                <th>Status</th>
                <th>Files</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id} className={selected?.id === user.id ? "selected-row" : ""} onClick={() => selectUser(user.id)}>
                  <td>{user.id}</td>
                  <td>{user.display_name}</td>
                  {showEmailCol && <td>{user.email}</td>}
                  <td><StatusBadge status={user.status} /></td>
                  <td><Link to={`/files?userId=${user.id}`} onClick={(e) => e.stopPropagation()}>Manage files</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        {selected ? (
          <section className="panel detail-panel">
            <h2>{selected.display_name}</h2>
            <dl className="detail-list">
              <div><dt>User ID</dt><dd>{selected.id}</dd></div>
              <div><dt>Email</dt><dd>{selected.email}</dd></div>
              <div><dt>Status</dt><dd><StatusBadge status={selected.status} /></dd></div>
              <div><dt>Registered</dt><dd>{formatDate(selected.created_at)}</dd></div>
              <div><dt>Last login</dt><dd>{formatDate(selected.last_login_at)}</dd></div>
              <div><dt>Files</dt><dd>{selected.file_count ?? 0}</dd></div>
            </dl>
            <StorageBar used={selected.storage_used_bytes} quota={selected.storage_quota_bytes} />

            <label>
              Reason (for quota changes)
              <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} />
            </label>

            <div className="action-row">
              <button className="btn btn-primary" type="button" onClick={() => openResetModal(selected)}>Reset Password</button>
            </div>

            <h3>Storage Quota</h3>
            <div className="quota-presets">
              {QUOTA_PRESETS.map((preset) => (
                <button key={preset.label} className="btn btn-secondary" type="button" onClick={() => runQuotaUpdate(preset.bytes)}>
                  {preset.label}
                </button>
              ))}
            </div>
            <div className="toolbar">
              <input className="input" placeholder="Custom amount" value={customQuota} onChange={(e) => setCustomQuota(e.target.value)} />
              <select className="input" value={quotaUnit} onChange={(e) => setQuotaUnit(e.target.value as "GB" | "TB")}>
                <option value="GB">GB</option>
                <option value="TB">TB</option>
              </select>
              <button
                className="btn btn-primary"
                type="button"
                onClick={() => {
                  const bytes = parseQuotaInput(customQuota, quotaUnit);
                  if (!bytes) {
                    setError("Enter a valid custom quota");
                    return;
                  }
                  runQuotaUpdate(bytes);
                }}
              >
                Apply Custom Quota
              </button>
            </div>
          </section>
        ) : null}
      </div>

      {resetTarget ? (
        <div className="modal-backdrop" onClick={() => setResetTarget(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h2>Reset password</h2>
            <p className="table-meta">
              Set a new password for <strong>{resetTarget.display_name}</strong> ({resetTarget.email}).
            </p>
            {resetError ? <div className="alert alert-error">{resetError}</div> : null}
            <label>
              New password (min 8 characters)
              <input
                className="input"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                autoFocus
              />
            </label>
            <label>
              Confirm new password
              <input
                className="input"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
            </label>
            <div className="modal-actions">
              <button className="btn btn-secondary" type="button" onClick={() => setResetTarget(null)}>Cancel</button>
              <button className="btn btn-primary" type="button" onClick={runResetPassword}>Save new password</button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
