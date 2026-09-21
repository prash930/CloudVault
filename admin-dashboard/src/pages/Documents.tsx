import { useEffect, useMemo, useState } from "react";
import { apiRequest, getToken, API_BASE_URL } from "../api/client";
import type { UsersFilesOverview, UserWithFiles } from "../types";
import StatusBadge from "../components/StatusBadge";
import { formatBytes, formatDate } from "../utils/format";

export default function Documents() {
  const [data, setData] = useState<UsersFilesOverview | null>(null);
  const [search, setSearch] = useState("");
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    apiRequest<UsersFilesOverview>("/admin/users/files-overview")
      .then(setData)
      .catch((err) => setError(String(err.message || err)));
  }, []);

  const filtered = useMemo(() => {
    if (!data) return [];
    const term = search.trim().toLowerCase();
    if (!term) return data.users;
    return data.users.filter(
      (u) =>
        u.display_name.toLowerCase().includes(term) ||
        u.email.toLowerCase().includes(term) ||
        u.files.some((f) => f.filename.toLowerCase().includes(term)),
    );
  }, [data, search]);

  function toggle(userId: number) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
  }

  async function downloadFile(file: { id: number; filename: string }) {
    try {
      const response = await fetch(`${API_BASE_URL}/admin/files/${file.id}/download`, {
        headers: { Authorization: `Bearer ${getToken()}` },
      });
      if (!response.ok) throw new Error("Download failed");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file.filename;
      anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
      setMessage(`Downloaded ${file.filename}`);
    } catch (err) {
      setError(String((err as Error).message || err));
    }
  }

  async function trashFile(file: { id: number; filename: string }) {
    try {
      await apiRequest(`/admin/files/${file.id}/trash`, { method: "POST" });
      setMessage(`Moved ${file.filename} to trash`);
      const refreshed = await apiRequest<UsersFilesOverview>("/admin/users/files-overview");
      setData(refreshed);
    } catch (err) {
      setError(String((err as Error).message || err));
    }
  }

  if (error) return <div className="alert alert-error">{error}</div>;
  if (!data) return <div className="page-loading">Loading documents…</div>;

  const totalDocs = data.users.reduce((sum, u) => sum + u.files.filter((f) => !f.is_folder).length, 0);

  return (
    <div className="page">
      <header className="page-header">
        <h1>User Documents</h1>
        <p>Every user and the documents they have uploaded, grouped under each user's name.</p>
      </header>

      {message ? <div className="alert alert-success">{message}</div> : null}

      <div className="toolbar">
        <input
          className="input"
          placeholder="Search user, email, or document name"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button className="btn btn-primary" type="button" onClick={() => setExpanded(new Set(filtered.map((u) => u.id)))}>
          Expand all
        </button>
        <button className="btn btn-secondary" type="button" onClick={() => setExpanded(new Set())}>
          Collapse all
        </button>
        <span className="table-meta" style={{ margin: 0, marginLeft: "auto" }}>
          {data.total} users · {totalDocs} documents · {formatBytes(data.users.reduce((s, u) => s + u.storage_used_bytes, 0))} used
        </span>
      </div>

      <div className="user-docs-list">
        {filtered.length === 0 ? (
          <section className="panel empty-state">No users or documents match your search.</section>
        ) : (
          filtered.map((user: UserWithFiles) => {
            const docs = user.files.filter((f) => !f.is_folder);
            const folders = user.files.filter((f) => f.is_folder);
            const isOpen = expanded.has(user.id);
            return (
              <section key={user.id} className="panel user-doc-card">
                <div className="user-doc-head" onClick={() => toggle(user.id)} role="button" tabIndex={0}>
                  <span className="avatar">{user.display_name.charAt(0).toUpperCase()}</span>
                  <div className="user-doc-title">
                    <strong>{user.display_name}</strong>
                    <span className="user-doc-email">{user.email}</span>
                  </div>
                  <StatusBadge status={user.status} />
                  <span className="user-doc-meta">
                    {docs.length} files · {formatBytes(user.storage_used_bytes)}
                  </span>
                  <span className="user-doc-toggle">{isOpen ? "−" : "+"}</span>
                </div>

                {isOpen ? (
                  <div className="user-doc-body">
                    {folders.length > 0 ? (
                      <div className="user-doc-group">
                        <h4>Folders</h4>
                        <ul className="doc-list">
                          {folders.map((folder) => (
                            <li key={folder.id} className="doc-item">
                              <span className="doc-name">📁 {folder.filename}</span>
                              <span className="doc-muted">Folder</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    ) : null}

                    <div className="user-doc-group">
                      <h4>Documents</h4>
                      {docs.length === 0 ? (
                        <p className="doc-muted empty-state">No documents uploaded.</p>
                      ) : (
                        <table>
                          <thead>
                            <tr>
                              <th>Name</th>
                              <th>Size</th>
                              <th>Uploaded</th>
                              <th>Actions</th>
                            </tr>
                          </thead>
                          <tbody>
                            {docs.map((file) => (
                              <tr key={file.id}>
                                <td>📄 {file.filename}</td>
                                <td>{formatBytes(file.size_bytes)}</td>
                                <td>{formatDate(file.created_at)}</td>
                                <td>
                                  <button
                                    className="btn btn-secondary"
                                    type="button"
                                    onClick={() => downloadFile(file)}
                                  >
                                    Download
                                  </button>
                                  <button
                                    className="btn btn-danger-outline"
                                    type="button"
                                    onClick={() => trashFile(file)}
                                    style={{ marginLeft: 6 }}
                                  >
                                    Trash
                                  </button>
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      )}
                    </div>
                  </div>
                ) : null}
              </section>
            );
          })
        )}
      </div>
    </div>
  );
}