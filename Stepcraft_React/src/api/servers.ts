import { apiRequest } from "./client";
import type {
  LoginResponse,
  GoogleLoginResponse,
  RegisterServerResponse,
  OwnedServersResponse,
  PlayersListResponse,
  RewardsResponse,
  ServerInfo,
  TogglePrivacyResponse,
  PushResponse,
  ClaimStatusResponse,
  YesterdayStepsResponse,
  BansResponse,
  ActionResponse,
} from "./types";

export async function login(email: string, password: string): Promise<LoginResponse> {
  return apiRequest<LoginResponse>("/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
}

export async function loginWithGoogle(idToken: string): Promise<GoogleLoginResponse> {
  return apiRequest<GoogleLoginResponse>("/v1/auth/google", {
    method: "POST",
    body: JSON.stringify({ id_token: idToken }),
  });
}

export async function getOwnedServers(token: string): Promise<OwnedServersResponse> {
  return apiRequest<OwnedServersResponse>("/v1/servers/owned", {}, token);
}

export async function registerServer(
  token: string,
  payload: {
    server_name: string;
    owner_name: string;
    owner_email: string;
    server_address?: string;
    server_version?: string;
    is_private: boolean;
    invite_code?: string;
  },
): Promise<RegisterServerResponse> {
  return apiRequest<RegisterServerResponse>(
    "/v1/servers/register",
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
    token,
  );
}

export async function getServerInfo(token: string, server: string): Promise<ServerInfo> {
  return apiRequest<ServerInfo>(`/v1/servers/info?server=${encodeURIComponent(server)}`, {}, token);
}

export async function togglePrivacy(
  token: string,
  server: string,
  isPrivate: boolean,
): Promise<TogglePrivacyResponse> {
  return apiRequest<TogglePrivacyResponse>(
    `/v1/servers/toggle-privacy?server=${encodeURIComponent(server)}`,
    {
      method: "POST",
      body: JSON.stringify({ is_private: isPrivate }),
    },
    token,
  );
}

export async function listPlayers(
  token: string,
  server: string,
  limit = 100,
  offset = 0,
  query?: string,
): Promise<PlayersListResponse> {
  const params = new URLSearchParams({
    server,
    limit: String(limit),
    offset: String(offset),
  });
  if (query) params.set("q", query);
  return apiRequest<PlayersListResponse>(`/v1/servers/players/list?${params.toString()}`, {}, token);
}

export async function getRewards(token: string, server: string): Promise<RewardsResponse> {
  return apiRequest<RewardsResponse>(`/v1/servers/rewards?server=${encodeURIComponent(server)}`, {}, token);
}

export async function updateRewards(token: string, server: string, tiers: RewardsResponse["tiers"]): Promise<RewardsResponse> {
  return apiRequest<RewardsResponse>(
    `/v1/servers/rewards?server=${encodeURIComponent(server)}`,
    {
      method: "PUT",
      body: JSON.stringify({ tiers }),
    },
    token,
  );
}

export async function useDefaultRewards(token: string, server: string): Promise<RewardsResponse> {
  return apiRequest<RewardsResponse>(
    `/v1/servers/rewards/default?server=${encodeURIComponent(server)}`,
    {
      method: "POST",
    },
    token,
  );
}

export async function listPush(token: string, server: string): Promise<PushResponse> {
  return apiRequest<PushResponse>(`/v1/servers/push?server=${encodeURIComponent(server)}`, {}, token);
}

export async function createPush(
  token: string,
  server: string,
  payload: { message: string; scheduled_at: string; timezone: string },
) {
  return apiRequest<PushResponse>(
    `/v1/servers/push?server=${encodeURIComponent(server)}`,
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
    token,
  );
}

export async function getClaimStatus(
  token: string,
  server: string,
  username: string,
): Promise<ClaimStatusResponse> {
  return apiRequest<ClaimStatusResponse>(
    `/v1/servers/players/${encodeURIComponent(username)}/claim-status?server=${encodeURIComponent(server)}`,
    {},
    token,
  );
}

export async function claimReward(
  token: string,
  server: string,
  username: string,
): Promise<ClaimStatusResponse> {
  return apiRequest<ClaimStatusResponse>(
    `/v1/servers/players/${encodeURIComponent(username)}/claim-reward?server=${encodeURIComponent(server)}`,
    { method: "POST" },
    token,
  );
}

export async function getYesterdaySteps(
  token: string,
  server: string,
  username: string,
): Promise<YesterdayStepsResponse> {
  return apiRequest<YesterdayStepsResponse>(
    `/v1/servers/players/${encodeURIComponent(username)}/yesterday-steps?server=${encodeURIComponent(server)}`,
    {},
    token,
  );
}

export async function listBans(token: string, server: string, limit = 1000): Promise<BansResponse> {
  return apiRequest<BansResponse>(
    `/v1/servers/bans?server=${encodeURIComponent(server)}&limit=${limit}`,
    {},
    token,
  );
}

export async function banPlayer(
  token: string,
  server: string,
  username: string,
  reason: string,
): Promise<ActionResponse> {
  return apiRequest<ActionResponse>(
    `/v1/servers/players/${encodeURIComponent(username)}/ban?server=${encodeURIComponent(server)}`,
    {
      method: "POST",
      body: JSON.stringify({ reason }),
    },
    token,
  );
}

export async function unbanPlayer(
  token: string,
  server: string,
  username: string,
): Promise<ActionResponse> {
  return apiRequest<ActionResponse>(
    `/v1/servers/players/${encodeURIComponent(username)}/ban?server=${encodeURIComponent(server)}`,
    { method: "DELETE" },
    token,
  );
}

export async function wipePlayer(
  token: string,
  server: string,
  username: string,
): Promise<ActionResponse> {
  return apiRequest<ActionResponse>(
    `/v1/servers/players/${encodeURIComponent(username)}?server=${encodeURIComponent(server)}`,
    { method: "DELETE" },
    token,
  );
}
