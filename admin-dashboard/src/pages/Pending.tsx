import { useEffect, useState } from "react";
import { apiRequest } from "../api/client";
import type { User } from "../types";
import StatusBadge from "../components/StatusBadge";
import { formatDate } from "../utils/format";

export default function Pending() {
  const [users, setUsers] = useState<User[]>([]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  async function load() {
    setUsers(await apiRequest<User[]>("/admin/users/pending"));
  }

  useEffect(() => {
    load().catch((err) => setError(String(err.message || err)));
  }, []);

  async function approve(userId: number) {
    await apiRequest(`/admin/users/${userId}/approve`, { method: "POST" });
    setMessage("User approved");
    await load();
  }

  async function reject(userId: number) {
    if (!window.confirm("Reject and delete this registration request?")) return;
    await apiRequest(`/admin/users/${userId}/reject`, { method: "POST" });
    setMessage("User rejected");
    await load();
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Pending Approvals</h1>
        <p>Review new registration requests before they can sign in</p>
      </header>
      {error ? <div className="alert alert-error">{error}</div> : null}
      {message ? <div className="alert alert-success">{message}</div> : null}
      <section className="panel">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Email</th>
              <th>Registered</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.length === 0 ? (
              <tr><td colSpan={6}>No pending users</td></tr>
            ) : users.map((user) => (
              <tr key={user.id}>
                <td>{user.id}</td>
                <td>{user.display_name}</td>
                <td>{user.email}</td>
                <td>{formatDate(user.created_at)}</td>
                <td><StatusBadge status={user.status} /></td>
                <td className="action-row">
                  <button className="btn btn-primary" type="button" onClick={() => approve(user.id)}>Approve</button>
                  <button className="btn btn-danger" type="button" onClick={() => reject(user.id)}>Reject</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
