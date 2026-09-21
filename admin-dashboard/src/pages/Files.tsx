import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { apiRequest, getToken, API_BASE_URL } from "../api/client";
import type { FileItem, User } from "../types";
import ConfirmDialog from "../components/ConfirmDialog";
import { formatBytes, formatDate } from "../utils/format";

export default function Files() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialUserId = searchParams.get("userId") || "";
  const [users, setUsers] = useState<User[]>([]);
  const [userId, setUserId] = useState(initialUserId);
  const [files, setFiles] = useState<FileItem[]>([]);
  const [parentFolderId, setParentFolderId] = useState<number | null>(null);
  const [breadcrumbs, setBreadcrumbs] = useState<Array<{ id: number | null; name: string }>>([{ id: null, name: "Root" }]);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState("name");
  const [showTrash, setShowTrash] = useState(false);
  const [selected, setSelected] = useState<FileItem | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    apiRequest<{ users: User[] }>("/admin/users?limit=1000").then((data) => setUsers(data.users));
  }, []);

  const selectedUser = useMemo(() => users.find((u) => String(u.id) === userId), [users, userId]);

  async function loadFiles(nextParent: number | null = parentFolderId) {
    if (!userId) return;
    const params = new URLSearchParams();
    if (showTrash) params.set("trashed", "true");
    else if (nextParent !== null) params.set("parent_folder_id", String(nextParent));
    if (search.trim()) params.set("search", search.trim());
    params.set("sort", sort);
    const data = await apiRequest<{ items: FileItem[] }>(`/admin/users/${userId}/files?${params.toString()}`);
    setFiles(data.items);
  }

  useEffect(() => {
    if (!userId) return;
    loadFiles().catch((err) => setError(String(err.message || err)));
  }, [userId, parentFolderId, sort, showTrash]);

  useEffect(() => {
    if (!selected || selected.is_folder) {
      setPreviewUrl(null);
      return;
    }
    const mime = selected.mime_type || "";
    const previewable = mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("text/") || mime === "application/pdf";
    if (!previewable) {
      setPreviewUrl(null);
      return;
    }
    let revoked: string | null = null;
    fetch(`${API_BASE_URL}/admin/files/${selected.id}/download?inline=true`, {
      headers: { Authorization: `Bearer ${getToken()}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error(`Preview failed (${res.status})`);
        return res.blob();
      })
      .then((blob) => {
        revoked = URL.createObjectURL(blob);
        setPreviewUrl(revoked);
      })
      .catch(() => setPreviewUrl(null));
    return () => {
      if (revoked) URL.revokeObjectURL(revoked);
    };
  }, [selected]);

  function openFolder(folder: FileItem) {
    setParentFolderId(folder.id);
    setBreadcrumbs((prev) => [...prev, { id: folder.id, name: folder.filename }]);
    setSelected(null);
  }

  function goToCrumb(index: number) {
    const crumb = breadcrumbs[index];
    setBreadcrumbs(breadcrumbs.slice(0, index + 1));
    setParentFolderId(crumb.id);
    setSelected(null);
  }

  async function downloadFile(file: FileItem) {
    try {
      const response = await fetch(`${API_BASE_URL}/admin/files/${file.id}/download`, {
        headers: { Authorization: `Bearer ${getToken()}` },
      });
      if (!response.ok) throw new Error(`Download failed (${response.status})`);
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

  async function trashFile(file: FileItem) {
    try {
      await apiRequest(`/admin/files/${file.id}/trash`, { method: "POST" });
      setMessage("Moved to trash");
      await loadFiles();
      setSelected(null);
    } catch (err) {
      setError(String((err as Error).message || err));
    }
  }

  async function restoreFile(file: FileItem) {
    try {
      await apiRequest(`/admin/files/${file.id}/restore`, { method: "POST" });
      setMessage("Restored from trash");
      await loadFiles();
      setSelected(null);
    } catch (err) {
      setError(String((err as Error).message || err));
    }
  }

  async function deleteFile(file: FileItem) {
    try {
      await apiRequest(`/admin/files/${file.id}`, { method: "DELETE" });
      setMessage("Permanently deleted");
      setConfirmDelete(false);
      await loadFiles();
      setSelected(null);
    } catch (err) {
      setConfirmDelete(false);
      setError(String((err as Error).message || err));
    }
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Files / Storage</h1>
        <p>Admin file management for a selected user. All access is audited.</p>
      </header>

      <div className="toolbar">
        <select className="input" value={userId} onChange={(e) => { setUserId(e.target.value); setSearchParams(e.target.value ? { userId: e.target.value } : {}); setParentFolderId(null); setBreadcrumbs([{ id: null, name: "Root" }]); }}>
          <option value="">Select user</option>
          {users.map((user) => (
            <option key={user.id} value={user.id}>{user.display_name} ({user.email})</option>
          ))}
        </select>
        <input className="input" placeholder="Search files" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select className="input" value={sort} onChange={(e) => setSort(e.target.value)}>
          <option value="name">Sort: Name</option>
          <option value="date">Sort: Date</option>
          <option value="size">Sort: Size</option>
          <option value="type">Sort: Type</option>
        </select>
        <button className="btn btn-primary" type="button" onClick={() => loadFiles()}>Search</button>
        <label className="checkbox-inline">
          <input type="checkbox" checked={showTrash} onChange={(e) => { setShowTrash(e.target.checked); setParentFolderId(null); }} />
          Show trash
        </label>
      </div>

      {selectedUser ? (
        <div className="alert alert-info">
          Managing files for <strong>{selectedUser.display_name}</strong> — {formatBytes(selectedUser.storage_used_bytes)} / {formatBytes(selectedUser.storage_quota_bytes)} used
        </div>
      ) : null}
      {error ? <div className="alert alert-error">{error}</div> : null}
      {message ? <div className="alert alert-success">{message}</div> : null}

      {!userId ? (
        <section className="panel empty-state">Select a user to browse their files.</section>
      ) : (
        <div className="split-view">
          <section className="panel">
            <div className="breadcrumbs">
              {breadcrumbs.map((crumb, index) => (
                <button key={`${crumb.id}-${index}`} type="button" className="link-btn" onClick={() => goToCrumb(index)}>
                  {crumb.name}
                </button>
              ))}
            </div>
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Size</th>
                  <th>Modified</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {files.length === 0 ? (
                  <tr><td colSpan={4}>No files in this view</td></tr>
                ) : files.map((file) => (
                  <tr key={file.id} className={selected?.id === file.id ? "selected-row" : ""} onClick={() => setSelected(file)} onDoubleClick={() => file.is_folder && !showTrash ? openFolder(file) : undefined}>
                    <td>{file.is_folder ? "📁" : "📄"} {file.filename}</td>
                    <td>{file.is_folder ? "—" : formatBytes(file.size_bytes)}</td>
                    <td>{formatDate(file.updated_at)}</td>
                    <td>{file.is_trashed ? "TRASH" : file.moderation_status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          {selected ? (
            <section className="panel detail-panel">
              <h2>{selected.filename}</h2>
              <dl className="detail-list">
                <div><dt>File ID</dt><dd>{selected.id}</dd></div>
                <div><dt>Type</dt><dd>{selected.mime_type || (selected.is_folder ? "Folder" : "Unknown")}</dd></div>
                <div><dt>Size</dt><dd>{selected.is_folder ? "—" : formatBytes(selected.size_bytes)}</dd></div>
                <div><dt>Created</dt><dd>{formatDate(selected.created_at)}</dd></div>
              </dl>
              {previewUrl ? (
                selected.mime_type?.startsWith("image/") ? (
                  <img className="preview-image" src={previewUrl} alt={selected.filename} />
                ) : selected.mime_type?.startsWith("video/") ? (
                  <video className="preview-frame" src={previewUrl} controls />
                ) : selected.mime_type === "application/pdf" ? (
                  <iframe className="preview-frame" src={previewUrl} title={selected.filename} />
                ) : (
                  <a className="inline-link" href={previewUrl} download={selected.filename}>Download preview</a>
                )
              ) : null}
              <div className="action-row">
                {!selected.is_folder && !selected.is_trashed ? (
                  <button className="btn btn-secondary" type="button" onClick={() => downloadFile(selected)}>Download</button>
                ) : null}
                {!selected.is_trashed ? (
                  <button className="btn btn-secondary" type="button" onClick={() => trashFile(selected)}>Move to Trash</button>
                ) : (
                  <button className="btn btn-secondary" type="button" onClick={() => restoreFile(selected)}>Restore</button>
                )}
                {selected.is_trashed ? (
                  <button className="btn btn-danger" type="button" onClick={() => setConfirmDelete(true)}>Permanently Delete</button>
                ) : null}
              </div>
            </section>
          ) : null}
        </div>
      )}

      <ConfirmDialog
        open={confirmDelete}
        title="Permanently delete file"
        message={`This cannot be undone. Delete "${selected?.filename}" forever?`}
        confirmLabel="Delete permanently"
        requireText="DELETE"
        dangerous
        onConfirm={() => selected && deleteFile(selected)}
        onCancel={() => setConfirmDelete(false)}
      />
    </div>
  );
}
