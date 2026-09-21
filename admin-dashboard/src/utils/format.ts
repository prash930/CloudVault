export function formatBytes(bytes: number): string {
  if (bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

export function formatDate(value?: string | null): string {
  if (!value) return "—";
  return new Date(value).toLocaleString();
}

export const QUOTA_PRESETS = [
  { label: "50 GB", bytes: 50 * 1024 ** 3 },
  { label: "100 GB", bytes: 100 * 1024 ** 3 },
  { label: "200 GB", bytes: 200 * 1024 ** 3 },
  { label: "500 GB", bytes: 500 * 1024 ** 3 },
  { label: "1 TB", bytes: 1024 ** 4 },
];

export function parseQuotaInput(value: string, unit: "GB" | "TB"): number | null {
  const num = Number(value);
  if (!Number.isFinite(num) || num <= 0) return null;
  return unit === "TB" ? num * 1024 ** 4 : num * 1024 ** 3;
}

export function statusClass(status: string): string {
  return `status-${status.toLowerCase()}`;
}
