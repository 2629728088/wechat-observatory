import { afterEach, describe, expect, it, vi } from "vitest";

import {
  SEND_ACTION_KINDS,
  buildSendActionBody,
  createApiKey,
  deleteApiKey,
  getContacts,
  getMessages,
  getModules,
  openLiveEvents,
  parseLiveMessageEvent,
  sendAction,
  sendText,
  setApiKeyEnabled,
  updateDevice
} from "@/api";

const baseAction = {
  password: "admin-password",
  device: "device-a",
  ownerWxid: "owner-a",
  wxid: "target-a"
};

function stubJSONResponse(body: unknown, status = 200) {
  const fetchMock = vi.fn(async () => {
    return new Response(typeof body === "string" ? body : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" }
    });
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function fetchCall(fetchMock: ReturnType<typeof stubJSONResponse>, index = 0) {
  return fetchMock.mock.calls[index] as unknown as [string, RequestInit];
}

describe("buildSendActionBody", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("keeps the admin action kind catalog aligned with backend protocol names", () => {
    expect(SEND_ACTION_KINDS).toEqual([
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
    ]);
    expect(new Set(SEND_ACTION_KINDS).size).toBe(SEND_ACTION_KINDS.length);
  });

  it("keeps legacy text action compatible with the admin endpoint", () => {
    expect(
      buildSendActionBody({
        ...baseAction,
        kind: "text",
        text: "hello"
      })
    ).toMatchObject({
      device: "device-a",
      owner_wxid: "owner-a",
      wx_ids: ["target-a"],
      kind: "text",
      text: "hello"
    });
  });

  it("maps media action fields to backend snake_case names", () => {
    expect(
      buildSendActionBody({
        ...baseAction,
        kind: "video",
        text: "caption",
        mediaKind: "video",
        mediaBase64: "data:video/mp4;base64,AA==",
        mediaURL: "/api/media/device-a/clip.mp4",
        mediaName: "clip.mp4",
        mediaMime: "video/mp4",
        mediaSize: 1234
      })
    ).toMatchObject({
      kind: "video",
      text: "caption",
      media_kind: "video",
      media_base64: "data:video/mp4;base64,AA==",
      media_url: "/api/media/device-a/clip.mp4",
      media_name: "clip.mp4",
      media_mime: "video/mp4",
      media_size: 1234
    });
  });

  it("maps emoji direct-send and source-forward fields", () => {
    expect(
      buildSendActionBody({
        ...baseAction,
        kind: "emoji",
        emojiMd5: "emoji-md5-a",
        emojiProductId: "product-a",
        sourceChatRecordId: 5005
      })
    ).toMatchObject({
      kind: "emoji",
      emoji_md5: "emoji-md5-a",
      emoji_product_id: "product-a",
      source_chat_record_id: 5005
    });
  });

  it("maps quote and appmsg construction fields", () => {
    expect(
      buildSendActionBody({
        ...baseAction,
        kind: "quote",
        text: "reply",
        quoteMsgId: 1001,
        quoteChatRecordId: 2002,
        quoteTalker: "room-a",
        quoteSenderWxid: "member-a",
        appmsgTitle: "Title",
        appmsgDescription: "Description",
        appmsgUrl: "https://example.test/article",
        appmsgAppName: "Example",
        appmsgThumbUrl: "https://example.test/thumb.png"
      })
    ).toMatchObject({
      kind: "quote",
      text: "reply",
      quote_msg_id: 1001,
      quote_chat_record_id: 2002,
      quote_talker: "room-a",
      quote_sender_wxid: "member-a",
      appmsg_title: "Title",
      appmsg_description: "Description",
      appmsg_url: "https://example.test/article",
      appmsg_app_name: "Example",
      appmsg_thumb_url: "https://example.test/thumb.png"
    });
  });

  it("maps mini program and chat history fields", () => {
    expect(
      buildSendActionBody({
        ...baseAction,
        kind: "mini_program",
        appmsgTitle: "Mini",
        miniProgramUsername: "gh_demo@app",
        miniProgramPagePath: "pages/index",
        miniProgramAppid: "wx-demo",
        miniProgramIconUrl: "https://example.test/icon.png",
        miniProgramVersion: 12,
        miniProgramType: 0,
        sourceChatRecordId: 3003
      })
    ).toMatchObject({
      kind: "mini_program",
      appmsg_title: "Mini",
      mini_program_username: "gh_demo@app",
      mini_program_page_path: "pages/index",
      mini_program_appid: "wx-demo",
      mini_program_icon_url: "https://example.test/icon.png",
      mini_program_version: 12,
      mini_program_type: 0,
      source_chat_record_id: 3003
    });

    expect(
      buildSendActionBody({
        ...baseAction,
        kind: "chat_history",
        recordTitle: "Records",
        recordDescription: "2 items",
        recorditemXml: "<recordinfo></recordinfo>",
        forwardOriginal: true,
        sourceChatRecordId: 4004,
        sourceChatRecordIds: [4004, 4005]
      })
    ).toMatchObject({
      kind: "chat_history",
      record_title: "Records",
      record_description: "2 items",
      recorditem_xml: "<recordinfo></recordinfo>",
      forward_original: true,
      source_chat_record_id: 4004,
      source_chat_record_ids: [4004, 4005]
    });
  });

  it("maps location metadata fields", () => {
    expect(
      buildSendActionBody({
        ...baseAction,
        kind: "location",
        locationLatitude: 30.1,
        locationLongitude: 120.2,
        locationScale: 16,
        locationLabel: "Office",
        locationPoiName: "POI",
        locationInfoUrl: "https://example.test/location",
        locationPoiId: "poi-a",
        locationFromPoiList: true,
        locationPoiTips: "tips"
      })
    ).toMatchObject({
      kind: "location",
      location_latitude: 30.1,
      location_longitude: 120.2,
      location_scale: 16,
      location_label: "Office",
      location_poiname: "POI",
      location_info_url: "https://example.test/location",
      location_poi_id: "poi-a",
      location_from_poi_list: true,
      location_poi_category_tips: "tips"
    });
  });

  it("sends admin password through the header and not the action JSON body", async () => {
    const fetchMock = stubJSONResponse({ ok: true, outbox_id: 1 });

    await sendAction({
      ...baseAction,
      kind: "text",
      text: "hello"
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [path, options] = fetchCall(fetchMock);
    expect(path).toBe("/api/send/action");
    expect(options.method).toBe("POST");
    expect(options.headers).toMatchObject({ "X-Bridge-Password": "admin-password" });
    expect(JSON.parse(options.body as string)).toMatchObject({
      device: "device-a",
      owner_wxid: "owner-a",
      wx_ids: ["target-a"],
      kind: "text",
      text: "hello"
    });
    expect(options.body as string).not.toContain("admin-password");
  });

  it("builds contact and message query strings with stable defaults", async () => {
    const fetchMock = stubJSONResponse({ contacts: [], messages: [] });

    await getContacts({
      password: "admin-password",
      device: "device-a",
      ownerWxid: "  owner-a  ",
      query: "  friend  ",
      includeDeleted: true
    });
    expect(fetchCall(fetchMock)[0]).toBe(
      "/api/module-contacts?device=device-a&limit=500&q=friend&owner_wxid=owner-a&include_deleted=1"
    );
    expect(fetchCall(fetchMock)[1].headers).toMatchObject({ "X-Bridge-Password": "admin-password" });

    await getMessages({
      password: "admin-password",
      device: "device-a",
      chatId: "chat-a",
      ownerWxid: " owner-a ",
      chatKind: "room",
      wxid: "wxid-a",
      limit: 77
    });
    expect(fetchCall(fetchMock, 1)[0]).toBe(
      "/api/messages?device=device-a&limit=77&chat_id=chat-a&owner_wxid=owner-a&chat_kind=room&wxid=wxid-a"
    );
  });

  it("keeps text send body compatible with the legacy endpoint", async () => {
    const fetchMock = stubJSONResponse({ ok: true, outbox_id: 10, queued: true, status_url: "/api/v1/outbox/10" });

    await sendText({
      password: "admin-password",
      device: "device-a",
      ownerWxid: "owner-a",
      wxid: "target-a",
      text: "hello"
    });

    const [path, options] = fetchCall(fetchMock);
    expect(path).toBe("/api/send/text");
    expect(options.method).toBe("POST");
    expect(options.headers).toMatchObject({ "X-Bridge-Password": "admin-password" });
    expect(JSON.parse(options.body as string)).toEqual({
      device: "device-a",
      owner_wxid: "owner-a",
      wx_ids: ["target-a"],
      text: "hello"
    });
  });

  it("maps admin api-key and device management helpers", async () => {
    const fetchMock = stubJSONResponse({ ok: true });

    await createApiKey({
      password: "admin-password",
      apiKey: "key-a",
      device: "device-a",
      nickname: "Device A"
    });
    expect(fetchCall(fetchMock)).toMatchObject([
      "/api/api-keys",
      {
        method: "POST",
        headers: { "X-Bridge-Password": "admin-password", "Content-Type": "application/json" },
        body: JSON.stringify({ api_key: "key-a", device: "device-a", nickname: "Device A" })
      }
    ]);

    await deleteApiKey({ password: "admin-password", apiKey: "key/a b" });
    expect(fetchCall(fetchMock, 1)[0]).toBe("/api/api-keys/key%2Fa%20b");
    expect(fetchCall(fetchMock, 1)[1]).toMatchObject({ method: "DELETE" });

    await setApiKeyEnabled({ password: "admin-password", apiKey: "key/a b", enabled: false });
    expect(fetchCall(fetchMock, 2)[0]).toBe("/api/api-keys/key%2Fa%20b/disable");
    expect(fetchCall(fetchMock, 2)[1]).toMatchObject({ method: "POST" });

    await updateDevice({ password: "admin-password", name: "device-a", nickname: "Device A" });
    expect(fetchCall(fetchMock, 3)[0]).toBe("/api/devices");
    expect(fetchCall(fetchMock, 3)[1]).toMatchObject({
      method: "POST",
      body: JSON.stringify({ name: "device-a", nickname: "Device A" })
    });
  });

  it("throws response text on failed requests", async () => {
    stubJSONResponse("invalid password", 401);

    await expect(getModules("bad-password")).rejects.toThrow("invalid password");
  });

  it("opens live events with an encoded password query", () => {
    const eventSource = vi.fn((url: string) => ({ url }));
    vi.stubGlobal("EventSource", eventSource);

    const source = openLiveEvents("p@ss word&x=1");

    expect(eventSource).toHaveBeenCalledWith("/api/live/events?password=p%40ss+word%26x%3D1");
    expect(source).toEqual({ url: "/api/live/events?password=p%40ss+word%26x%3D1" });
  });

  it("parses live message events without altering protocol fields", () => {
    expect(
      parseLiveMessageEvent(
        JSON.stringify({
          id: "event-a",
          chat_record_id: 12,
          device: "device-a",
          direction: "recv",
          text: "hello",
          message_type: 1,
          kind: "text"
        })
      )
    ).toMatchObject({
      id: "event-a",
      chat_record_id: 12,
      device: "device-a",
      direction: "recv",
      text: "hello",
      message_type: 1,
      kind: "text"
    });
  });
});
