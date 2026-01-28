export interface LoginResponse {
  token: string;
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
}

export interface YesterdayStepsResponse {
  minecraft_username: string;
  server_name: string;
  day: string;
  steps_yesterday: number;
}

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
