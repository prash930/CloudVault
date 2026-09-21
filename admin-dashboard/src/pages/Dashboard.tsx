import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiRequest } from "../api/client";
import type { DashboardStats } from "../types";
import { formatBytes, formatDate } from "../utils/format";

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    apiRequest<DashboardStats>("/admin/dashboard")
      .then(setStats)
      .catch((err) => setError(String(err.message || err)));
  }, []);

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!stats) return <div className="page-loading">Loading dashboard…</div>;

  const cards = [
    { label: "Total Users", value: stats.total_users, link: "/users" },
    { label: "Active Users", value: stats.active_users },
    { label: "Total Documents", value: stats.total_files, link: "/documents" },
    { label: "Storage Used", value: formatBytes(stats.total_storage_used_bytes) },
    { label: "Quota Allocated", value: formatBytes(stats.total_quota_bytes) },
  ];

  return (
    <div className="page">
      <header className="page-header">
        <h1>Dashboard</h1>
        <p>Overview of CloudBox service and quick access to user documents and accounts</p>
      </header>

      <div className="stat-grid">
        {cards.map((card) => (
          <div key={card.label} className="stat-card">
            <span>{card.label}</span>
            <strong>{card.value}</strong>
            {card.link ? <Link to={card.link}>View</Link> : null}
          </div>
        ))}
      </div>

      <div className="quick-links">
        <Link to="/documents" className="quick-link-card">
          <strong>User Documents</strong>
          <span>Browse all documents uploaded by every user, grouped under each user's name.</span>
        </Link>
        <Link to="/users" className="quick-link-card">
          <strong>Users &amp; Accounts</strong>
          <span>Manage every user's name, email, password resets, status, and storage quota.</span>
        </Link>
      </div>

      <div className="two-col">
        <section className="panel">
          <h2>Recent Uploads</h2>
          <table>
            <thead>
              <tr>
                <th>File</th>
                <th>User</th>
                <th>Size</th>
                <th>When</th>
              </tr>
            </thead>
            <tbody>
              {stats.recent_uploads.length === 0 ? (
                <tr><td colSpan={4}>No uploads yet</td></tr>
              ) : stats.recent_uploads.map((item) => (
                <tr key={item.id}>
                  <td>{item.filename}</td>
                  <td>{item.user_email}</td>
                  <td>{formatBytes(item.size_bytes)}</td>
                  <td>{formatDate(item.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="panel">
          <h2>Recent Admin Actions</h2>
          <table>
            <thead>
              <tr>
                <th>Action</th>
                <th>User</th>
                <th>When</th>
              </tr>
            </thead>
            <tbody>
              {stats.recent_admin_actions.length === 0 ? (
                <tr><td colSpan={3}>No admin actions yet</td></tr>
              ) : stats.recent_admin_actions.map((item) => (
                <tr key={item.id}>
                  <td>{item.action}</td>
                  <td>{item.user_id ?? "—"}</td>
                  <td>{formatDate(item.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Link className="inline-link" to="/audit-logs">View all audit logs</Link>
        </section>
      </div>
    </div>
  );
}
