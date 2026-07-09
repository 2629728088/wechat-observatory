import type { ApiKey, LiveMessageEvent, ModuleContact, ModuleStatus, StoredMessage } from "@/types";

type ApiOptions = {
  password: string;
  method?: "GET" | "POST" | "DELETE";
  body?: unknown;
};

async function requestJSON<T>(path: string, options: ApiOptions): Promise<T> {
  const headers: Record<string, string> = { "X-Bridge-Password": options.password };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const response = await fetch(path, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || `HTTP ${response.status}`);
  }
  return JSON.parse(text) as T;
}

export async function getModules(password: string) {
  return requestJSON<{ modules: ModuleStatus[] }>("/api/modules/status", { password });
}

export async function getApiKeys(password: string) {
  return requestJSON<{ api_keys?: ApiKey[] }>("/api/api-keys?limit=200", { password });
}

export async function createApiKey(params: {
  password: string;
  apiKey?: string;
  device?: string;
  nickname?: string;
}) {
  return requestJSON<{ ok: boolean; api_key?: ApiKey }>("/api/api-keys", {
    password: params.password,
    method: "POST",
    body: {
      api_key: params.apiKey,
      device: params.device,
      nickname: params.nickname
    }
  });
}

export async function deleteApiKey(params: { password: string; apiKey: string }) {
  return requestJSON<{ ok: boolean }>(`/api/api-keys/${encodeURIComponent(params.apiKey)}`, {
    password: params.password,
    method: "DELETE"
  });
}

export async function setApiKeyEnabled(params: { password: string; apiKey: string; enabled: boolean }) {
  return requestJSON<{ ok: boolean; api_key?: ApiKey }>(
    `/api/api-keys/${encodeURIComponent(params.apiKey)}/${params.enabled ? "enable" : "disable"}`,
    {
      password: params.password,
      method: "POST"
    }
  );
}

export async function updateDevice(params: { password: string; name: string; nickname?: string }) {
  return requestJSON<{ ok: boolean; device: ModuleStatus }>("/api/devices", {
    password: params.password,
    method: "POST",
    body: {
      name: params.name,
      nickname: params.nickname
    }
  });
}

export async function getContacts(params: {
  password: string;
  device: string;
  ownerWxid?: string;
  query: string;
  includeDeleted: boolean;
  limit?: number;
}) {
  const search = new URLSearchParams({
    device: params.device,
    limit: String(params.limit ?? 500)
  });
  if (params.query.trim()) {
    search.set("q", params.query.trim());
  }
  if (params.ownerWxid?.trim()) {
    search.set("owner_wxid", params.ownerWxid.trim());
  }
  if (params.includeDeleted) {
    search.set("include_deleted", "1");
  }
  return requestJSON<{ contacts: ModuleContact[] }>(`/api/module-contacts?${search}`, {
    password: params.password
  });
}

export async function getMessages(params: {
  password: string;
  device: string;
  wxid?: string;
  ownerWxid?: string;
  chatId?: string;
  chatKind?: string;
  limit?: number;
}) {
  const search = new URLSearchParams({
    device: params.device,
    limit: String(params.limit ?? 120)
  });
  if (params.chatId) {
    search.set("chat_id", params.chatId);
  }
  if (params.ownerWxid?.trim()) {
    search.set("owner_wxid", params.ownerWxid.trim());
  }
  if (params.chatKind) {
    search.set("chat_kind", params.chatKind);
  }
  if (params.wxid) {
    search.set("wxid", params.wxid);
  }
  return requestJSON<{ messages: StoredMessage[] }>(`/api/messages?${search}`, {
    password: params.password
  });
}

export async function sendText(params: {
  password: string;
  device: string;
  ownerWxid: string;
  wxid: string;
  text: string;
}) {
  return requestJSON<{ ok: boolean; outbox_id?: number; queued?: boolean; status_url?: string }>("/api/send/text", {
    password: params.password,
    method: "POST",
    body: {
      device: params.device,
      owner_wxid: params.ownerWxid,
      wx_ids: [params.wxid],
      text: params.text
    }
  });
}

export const SEND_ACTION_KINDS = [
  "text",
  "image",
  "video",
  "voice",
  "file",
  "emoji",
  "location",
  "quote",
  "link",
  "mini_program",
  "chat_history",
  "revoke"
] as const;

export type SendActionKind = (typeof SEND_ACTION_KINDS)[number];

export type SendActionParams = {
  password: string;
  device: string;
  ownerWxid: string;
  wxid: string;
  kind: SendActionKind;
  text?: string;
  mediaKind?: string;
  mediaBase64?: string;
  mediaURL?: string;
  mediaName?: string;
  mediaMime?: string;
  mediaSize?: number;
  quoteMsgId?: number;
  quoteChatRecordId?: number;
  quoteTalker?: string;
  quoteSenderWxid?: string;
  appmsgTitle?: string;
  appmsgDescription?: string;
  appmsgUrl?: string;
  appmsgAppName?: string;
  appmsgThumbUrl?: string;
  miniProgramUsername?: string;
  miniProgramPagePath?: string;
  miniProgramAppid?: string;
  miniProgramIconUrl?: string;
  miniProgramVersion?: number;
  miniProgramType?: number;
  emojiMd5?: string;
  emojiProductId?: string;
  recordTitle?: string;
  recordDescription?: string;
  recorditemXml?: string;
  forwardOriginal?: boolean;
  sourceChatRecordId?: number;
  sourceChatRecordIds?: number[];
  locationLatitude?: number;
  locationLongitude?: number;
  locationScale?: number;
  locationLabel?: string;
  locationPoiName?: string;
  locationInfoUrl?: string;
  locationPoiId?: string;
  locationFromPoiList?: boolean;
  locationPoiTips?: string;
};

export function buildSendActionBody(params: SendActionParams) {
  return {
    device: params.device,
    owner_wxid: params.ownerWxid,
    wx_ids: [params.wxid],
    kind: params.kind,
    text: params.text,
    media_kind: params.mediaKind,
    media_base64: params.mediaBase64,
    media_url: params.mediaURL,
    media_name: params.mediaName,
    media_mime: params.mediaMime,
    media_size: params.mediaSize,
    quote_msg_id: params.quoteMsgId,
    quote_chat_record_id: params.quoteChatRecordId,
    quote_talker: params.quoteTalker,
    quote_sender_wxid: params.quoteSenderWxid,
    appmsg_title: params.appmsgTitle,
    appmsg_description: params.appmsgDescription,
    appmsg_url: params.appmsgUrl,
    appmsg_app_name: params.appmsgAppName,
    appmsg_thumb_url: params.appmsgThumbUrl,
    mini_program_username: params.miniProgramUsername,
    mini_program_page_path: params.miniProgramPagePath,
    mini_program_appid: params.miniProgramAppid,
    mini_program_icon_url: params.miniProgramIconUrl,
    mini_program_version: params.miniProgramVersion,
    mini_program_type: params.miniProgramType,
    emoji_md5: params.emojiMd5,
    emoji_product_id: params.emojiProductId,
    record_title: params.recordTitle,
    record_description: params.recordDescription,
    recorditem_xml: params.recorditemXml,
    forward_original: params.forwardOriginal,
    source_chat_record_id: params.sourceChatRecordId,
    source_chat_record_ids: params.sourceChatRecordIds,
    location_latitude: params.locationLatitude,
    location_longitude: params.locationLongitude,
    location_scale: params.locationScale,
    location_label: params.locationLabel,
    location_poiname: params.locationPoiName,
    location_info_url: params.locationInfoUrl,
    location_poi_id: params.locationPoiId,
    location_from_poi_list: params.locationFromPoiList,
    location_poi_category_tips: params.locationPoiTips
  };
}

export async function sendAction(params: SendActionParams) {
  return requestJSON<{ ok: boolean; outbox_id?: number; queued?: boolean; status_url?: string }>("/api/send/action", {
    password: params.password,
    method: "POST",
    body: buildSendActionBody(params)
  });
}

export function openLiveEvents(password: string) {
  const search = new URLSearchParams({ password });
  return new EventSource(`/api/live/events?${search}`);
}

export function parseLiveMessageEvent(raw: string) {
  return JSON.parse(raw) as LiveMessageEvent;
}
