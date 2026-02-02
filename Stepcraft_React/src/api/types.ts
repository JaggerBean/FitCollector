export interface LoginResponse {
  token: string;
}

export interface RegisterUserResponse {
  ok: boolean;
  message: string;
}

export interface GoogleLoginResponse {
  token: string;
}

export interface RegisterServerResponse {
  api_key: string;
  server_name: string;
  message: string;
  is_private?: boolean;
  invite_code?: string | null;
}

export interface ClaimStatusResponse {
  claimed: boolean;
  claimed_at: string | null;
  day?: string;
  min_steps?: number;
  already_claimed?: boolean;
}

export interface DayStepsResponse {
  minecraft_username: string;
  server_name: string;
  day: string;
  steps_today: number;
  steps_yesterday?: number;
}

export type YesterdayStepsResponse = DayStepsResponse;

export interface BanEntry {
  ban_group_id: string;
  username: string | null;
  devices: string[];
  reason: string;
  banned_at: string | null;
}

export interface BansResponse {
  server_name: string;
  total_bans: number;
  bans: BanEntry[];
}

export interface ActionResponse {
  [key: string]: unknown;
}

export interface ServerSummary {
  server_name: string;
  owner_email?: string;
  server_address?: string;
  server_version?: string;
  created_at?: string;
  is_private?: boolean;
  invite_code?: string | null;
  is_active?: boolean;
  is_deleted?: boolean;
}

export interface OwnedServersResponse {
  servers: ServerSummary[];
}

export interface ServerInfo {
  server_name: string;
  max_players?: number | null;
  created_at?: string;
  last_used?: string | null;
  active?: boolean;
  is_private?: boolean;
  invite_code?: string | null;
  claim_buffer_days?: number | null;
  current_players?: number;
  slots_available?: number | null;
}

export interface TogglePrivacyResponse {
  ok: boolean;
  is_private: boolean;
  invite_code?: string | null;
}

export interface PlayersListResponse {
  server_name: string;
  total_players: number;
  players: Array<{
    minecraft_username: string;
    device_id: string;
    created_at?: string;
    last_used?: string | null;
    active?: boolean;
  }>;
  limit: number;
  offset: number;
}

export interface RewardsTier {
  min_steps: number;
  label: string;
  item_id?: string | null;
  rewards: string[];
}

export interface RewardsResponse {
  server_name: string;
  tiers: RewardsTier[];
  is_default?: boolean;
}

export interface PushItem {
  id: number;
  message: string;
  scheduled_at: string;
  created_at: string;
}

export interface PushResponse {
  server_name: string;
  items: PushItem[];
}

export interface PushCreateResponse {
  server_name: string;
  item: PushItem;
}

export interface InactivePruneSettingsResponse {
  server_name: string;
  enabled: boolean;
  max_inactive_days: number | null;
  mode: "deactivate" | "wipe";
}

export interface InactivePruneRunResponse {
  server_name: string;
  dry_run: boolean;
  mode: "deactivate" | "wipe";
  max_inactive_days: number;
  total_candidates?: number;
  total_removed?: number;
  candidates?: Array<{
    minecraft_username: string;
    device_id: string;
    last_claimed_at: string | null;
    created_at: string;
  }>;
  removed_players?: string[];
  records_affected?: Record<string, number>;
}

export interface ClaimWindowResponse {
  server_name: string;
  claim_buffer_days: number;
}
