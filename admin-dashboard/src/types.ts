export interface User {
  id: number;
  email: string;
  display_name: string;
  role: string;
  status: string;
  storage_quota_bytes: number;
  storage_used_bytes: number;
  created_at: string;
  updated_at?: string;
  last_login_at?: string | null;
  file_count?: number;
  hashed_password?: string;
}

export interface DashboardStats {
  total_users: number;
  active_users: number;
  pending_users: number;
  warned_users: number;
  suspended_users: number;
  banned_users: number;
  total_files: number;
  total_storage_used_bytes: number;
  total_quota_bytes: number;
  recent_uploads: Array<{
    id: number;
    filename: string;
    user_id: number;
    user_email: string;
    size_bytes: number;
    mime_type?: string;
    created_at: string;
  }>;
  recent_admin_actions: Array<{
    id: number;
    action: string;
    admin_id?: number;
    user_id?: number;
    file_id?: number;
    reason?: string;
    created_at: string;
  }>;
}

export interface FileItem {
  id: number;
  filename: string;
  original_filename: string;
  mime_type?: string;
  size_bytes: number;
  is_folder: boolean;
  parent_folder_id?: number | null;
  is_trashed: boolean;
  moderation_status: string;
  created_at: string;
  updated_at: string;
}

export interface AuditLog {
  id: number;
  admin_id?: number;
  user_id?: number;
  file_id?: number;
  action: string;
  reason?: string;
  details?: string;
  ip_address?: string;
  created_at: string;
}

export interface AdminSettings {
  default_quota_bytes: number;
  require_registration_approval: boolean;
  max_upload_bytes: number;
  trash_retention_days: number;
  storage_provider: string;
}

export interface GoogleDriveStatus {
  configured: boolean;
  connected: boolean;
  account_email?: string | null;
  account_id?: string | null;
  connected_at?: string | null;
  status: "connected" | "disconnected";
  total_space?: number | null;
  used_space?: number | null;
  available_space?: number | null;
}

export interface TelegramDriveStatus {
  configured: boolean;
  connected: boolean;
  bot_username?: string | null;
  chat_id?: string | null;
  connected_at?: string | null;
  status: "connected" | "disconnected";
  used_space?: number | null;
  note?: string | null;
}

export interface StorageStatus {
  storage_provider: string;
  google_drive: GoogleDriveStatus;
  telegram_drive: TelegramDriveStatus;
}
