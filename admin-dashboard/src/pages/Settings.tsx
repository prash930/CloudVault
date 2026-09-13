import { FormEvent, useEffect, useState } from "react";
import { apiRequest } from "../api/client";
import type { AdminSettings, StorageStatus } from "../types";
import { formatBytes, parseQuotaInput } from "../utils/format";

export default function Settings() {
  const [settings, setSettings] = useState<AdminSettings | null>(null);
  const [storageStatus, setStorageStatus] = useState<StorageStatus | null>(null);
  const [defaultQuotaGb, setDefaultQuotaGb] = useState("");
  const [maxUploadGb, setMaxUploadGb] = useState("");
  const [trashDays, setTrashDays] = useState("30");
  const [storageProvider, setStorageProvider] = useState("local");
  const [telegramBotToken, setTelegramBotToken] = useState("");
  const [telegramChatId, setTelegramChatId] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [storageBusy, setStorageBusy] = useState(false);

  useEffect(() => {
    Promise.all([
      apiRequest<AdminSettings>("/admin/settings"),
      apiRequest<StorageStatus>("/admin/storage/status"),
    ])
      .then(([settingsData, storageData]) => {
        setSettings(settingsData);
        setStorageStatus(storageData);
        setDefaultQuotaGb(String(Math.round(settingsData.default_quota_bytes / 1024 ** 3)));
        setMaxUploadGb(String(Math.round(settingsData.max_upload_bytes / 1024 ** 3)));
        setTrashDays(String(settingsData.trash_retention_days));
        setStorageProvider(storageData.storage_provider);
      })
      .catch((err) => setError(String(err.message || err)));
  }, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setMessage("");
    const defaultQuota = parseQuotaInput(defaultQuotaGb, "GB");
    const maxUpload = parseQuotaInput(maxUploadGb, "GB");
    const retention = Number(trashDays);
    if (!defaultQuota || !maxUpload || !Number.isFinite(retention) || retention < 0) {
      setError("Check numeric values for quota, upload limit, and trash retention.");
      return;
    }
    setSaving(true);
    try {
      const updated = await apiRequest<AdminSettings>("/admin/settings", {
        method: "PUT",
        body: JSON.stringify({
          default_quota_bytes: defaultQuota,
          max_upload_bytes: maxUpload,
          trash_retention_days: Math.floor(retention),
        }),
      });
      setSettings(updated);
      setMessage("System settings saved. New registrations use these defaults.");
    } catch (err) {
      setError(String(err instanceof Error ? err.message : err));
    } finally {
      setSaving(false);
    }
  }

  async function handleConnectTelegramDrive() {
    if (!telegramBotToken.trim() || !telegramChatId.trim()) {
      setError("Enter the Telegram bot token and storage chat ID.");
      return;
    }
    setStorageBusy(true);
    setError("");
    setMessage("");
    try {
      const status = await apiRequest<StorageStatus>("/admin/storage/telegram/connect", {
        method: "POST",
        body: JSON.stringify({
          bot_token: telegramBotToken.trim(),
          chat_id: telegramChatId.trim(),
        }),
      });
      setStorageStatus(status);
      setStorageProvider(status.storage_provider);
      setTelegramBotToken("");
      setMessage("Telegram Drive connected. Select it as the provider for new uploads.");
    } catch (err) {
      setError(String(err instanceof Error ? err.message : err));
    } finally {
      setStorageBusy(false);
    }
  }

  async function handleDisconnectTelegramDrive() {
    setStorageBusy(true);
    setError("");
    setMessage("");
    try {
      await apiRequest("/admin/storage/telegram/disconnect", { method: "POST" });
      const status = await apiRequest<StorageStatus>("/admin/storage/status");
      setStorageStatus(status);
      if (storageProvider === "telegram_drive") {
        setStorageProvider("local");
      }
      setMessage("Telegram Drive disconnected.");
    } catch (err) {
      setError(String(err instanceof Error ? err.message : err));
    } finally {
      setStorageBusy(false);
    }
  }

  async function handleStorageProviderChange(nextProvider: string) {
    setStorageBusy(true);
    setError("");
    setMessage("");
    try {
      const status = await apiRequest<StorageStatus>("/admin/storage/provider", {
        method: "PUT",
        body: JSON.stringify({ storage_provider: nextProvider }),
      });
      setStorageStatus(status);
      setStorageProvider(status.storage_provider);
      setMessage(
        nextProvider === "telegram_drive"
          ? "New uploads will use Telegram Drive. Existing files stay on their original backend."
          : "New uploads will use local storage.",
      );
    } catch (err) {
      setError(String(err instanceof Error ? err.message : err));
    } finally {
      setStorageBusy(false);
    }
  }

  if (!settings && !error) {
    return <div className="page-loading">Loading settings…</div>;
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Settings</h1>
        <p>Global defaults for registration, uploads, and storage</p>
      </header>

      {error ? <div className="alert alert-error">{error}</div> : null}
      {message ? <div className="alert alert-success">{message}</div> : null}

      <section className="panel settings-form">
        <h2>Storage</h2>
        <p className="panel-help">
          Configure cloud-backed storage for your CloudBox uploads. Telegram Drive is the primary unlimited storage backend.
        </p>

        <div className="storage-status-grid">
          <div>
            <span className="stat-label">Active provider</span>
            <strong>
              {storageProvider === "telegram_drive"
                ? "Telegram Drive"
                : "Local disk"}
            </strong>
          </div>
          <div>
            <span className="stat-label">Telegram Drive</span>
            <strong>{storageStatus?.telegram_drive?.connected ? "Connected" : "Disconnected"}</strong>
          </div>
          {storageStatus?.telegram_drive?.bot_username ? (
            <div>
              <span className="stat-label">Telegram Bot</span>
              <strong>@{storageStatus.telegram_drive.bot_username}</strong>
            </div>
          ) : null}
          {storageStatus?.telegram_drive?.chat_id ? (
            <div>
              <span className="stat-label">Storage Chat ID</span>
              <strong>{storageStatus.telegram_drive.chat_id}</strong>
            </div>
          ) : null}
          {storageStatus?.telegram_drive?.used_space !== undefined && storageStatus.telegram_drive.used_space !== null ? (
            <div>
              <span className="stat-label">Telegram used</span>
              <strong>{formatBytes(storageStatus.telegram_drive.used_space)}</strong>
            </div>
          ) : null}
        </div>

        <div style={{ marginTop: "1rem", padding: "1rem", background: "rgba(0,136,204,0.06)", borderRadius: "8px", border: "1px solid rgba(0,136,204,0.2)" }}>
          <h3 style={{ margin: "0 0 0.5rem 0", color: "#0088cc" }}>Telegram Drive Setup</h3>
          <p style={{ fontSize: "0.85rem", color: "#555", marginBottom: "0.75rem" }}>
            Uses Telegram Bot API to store chunked files in a dedicated storage chat or channel with unlimited capacity.
          </p>
          {!storageStatus?.telegram_drive?.connected ? (
            <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem", alignItems: "center" }}>
              <input
                className="input"
                style={{ flex: "1 1 200px" }}
                placeholder="Bot Token (e.g. 123456:ABC-DEF...)"
                value={telegramBotToken}
                onChange={(e) => setTelegramBotToken(e.target.value)}
              />
              <input
                className="input"
                style={{ flex: "1 1 140px" }}
                placeholder="Storage Chat ID"
                value={telegramChatId}
                onChange={(e) => setTelegramChatId(e.target.value)}
              />
              <button
                className="btn btn-primary"
                type="button"
                disabled={storageBusy}
                onClick={handleConnectTelegramDrive}
              >
                {storageBusy ? "Connecting…" : "Connect Telegram Drive"}
              </button>
            </div>
          ) : (
            <div style={{ display: "flex", gap: "0.75rem", alignItems: "center" }}>
              <span style={{ color: "#2e7d32", fontWeight: 500 }}>✓ Telegram Drive is connected</span>
              <button
                className="btn btn-secondary"
                type="button"
                disabled={storageBusy}
                onClick={handleDisconnectTelegramDrive}
              >
                Disconnect Telegram Drive
              </button>
            </div>
          )}
        </div>

        <div style={{ marginTop: "1rem" }}>
          <label>
            Storage provider for new uploads
            <select
              className="input"
              value={storageProvider}
              disabled={storageBusy}
              onChange={(e) => handleStorageProviderChange(e.target.value)}
            >
              <option value="telegram_drive">
                Telegram Drive (Recommended / Unlimited)
              </option>
              <option value="local">Local disk</option>
            </select>
            <small>
              Existing files keep their original backend. New uploads will route to the selected provider.
            </small>
          </label>
        </div>
      </section>

      <form className="panel settings-form" onSubmit={handleSubmit}>
        <h2>Registration &amp; quotas</h2>
        <label>
          Default quota for new users (GB)
          <input
            className="input"
            type="number"
            min={1}
            step={1}
            value={defaultQuotaGb}
            onChange={(e) => setDefaultQuotaGb(e.target.value)}
            required
          />
          {settings ? (
            <small>Current: {formatBytes(settings.default_quota_bytes)}</small>
          ) : null}
        </label>

        <label>
          Maximum single upload size (GB)
          <input
            className="input"
            type="number"
            min={1}
            step={1}
            value={maxUploadGb}
            onChange={(e) => setMaxUploadGb(e.target.value)}
            required
          />
        </label>

        <label>
          Trash retention (days)
          <input
            className="input"
            type="number"
            min={0}
            step={1}
            value={trashDays}
            onChange={(e) => setTrashDays(e.target.value)}
            required
          />
        </label>

        <button className="btn btn-primary" type="submit" disabled={saving}>
          {saving ? "Saving…" : "Save settings"}
        </button>
      </form>
    </div>
  );
}
