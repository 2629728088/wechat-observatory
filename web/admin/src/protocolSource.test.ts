import { describe, expect, it } from "vitest";

import { SEND_ACTION_KINDS } from "@/api";

async function readRepoText(pathFromRoot: string) {
  const fs = await import(/* @vite-ignore */ "node:" + "fs/promises");
  const path = await import(/* @vite-ignore */ "node:" + "path");
  const cwd = (globalThis as { process?: { cwd: () => string } }).process?.cwd();
  if (!cwd) {
    throw new Error("process.cwd is unavailable");
  }
  return fs.readFile(path.resolve(cwd, "../..", pathFromRoot), "utf8") as Promise<string>;
}

function extractGoOutboxKinds(source: string) {
  return [...source.matchAll(/OutboxKind[A-Za-z]+\s*=\s*"([^"]+)"/g)].map((match) => match[1]);
}

function extractOpenAPISendActionKinds(source: string) {
  const match = source.match(/"SendActionRequest"[\s\S]*?"kind"\s*:\s*\{[\s\S]*?"enum"\s*:\s*(\[[^\]]+\])/);
  if (!match) {
    throw new Error("SendActionRequest kind enum not found");
  }
  return JSON.parse(match[1]) as string[];
}

function extractOpenAPIJSON(source: string) {
  const match = source.match(/const openAPIJSONDocument = `([\s\S]*?)`\n/);
  if (!match) {
    throw new Error("openAPIJSONDocument not found");
  }
  return JSON.parse(match[1]) as {
    paths: Record<string, Record<string, unknown>>;
    components?: { responses?: Record<string, unknown> };
  };
}

function collectRefs(value: unknown): string[] {
  if (!value || typeof value !== "object") {
    return [];
  }
  if (Array.isArray(value)) {
    return value.flatMap((item) => collectRefs(item));
  }
  const record = value as Record<string, unknown>;
  const ownRef = typeof record.$ref === "string" ? [record.$ref] : [];
  return [...ownRef, ...Object.values(record).flatMap((item) => collectRefs(item))];
}

function expandResponseRefs(spec: ReturnType<typeof extractOpenAPIJSON>, refs: string[]) {
  const expanded = new Set(refs);
  for (const ref of refs) {
    const responseName = ref.match(/^#\/components\/responses\/(.+)$/)?.[1];
    const response = responseName ? spec.components?.responses?.[responseName] : undefined;
    if (response) {
      collectRefs(response).forEach((nestedRef) => expanded.add(nestedRef));
    }
  }
  return [...expanded];
}

function extractPublicMessageRoutes(source: string) {
  return [...source.matchAll(/"POST \/api\/v1\/messages\/([^"]+)",\s*s\.requirePublicAPI\(s\.sendV1Message\(([^)]*)\)\)/g)]
    .map((match) => ({
      pathKind: match[1],
      goKind: match[2].trim()
    }))
    .filter((route) => route.pathKind !== "action");
}

function extractGoOutboxKindNames(source: string) {
  return Object.fromEntries(
    [...source.matchAll(/(OutboxKind[A-Za-z]+)\s*=\s*"([^"]+)"/g)].map((match) => [match[1], match[2]])
  ) as Record<string, string>;
}

function extractCapabilitySendMappings(source: string) {
  return source
    .split("\n")
    .filter((line) => line.includes("SendKind:") && line.includes("SendEndpoint:"))
    .map((line) => {
      const kind = line.match(/SendKind:\s*(OutboxKind[A-Za-z]+)/)?.[1];
      const endpoint = line.match(/SendEndpoint:\s*"([^"]+)"/)?.[1];
      if (!kind || !endpoint) {
        throw new Error("capability send mapping is incomplete: " + line.trim());
      }
      return { endpoint, goKind: kind };
    });
}

function extractPublicStructJSONFields(source: string) {
  const fields: Record<string, string[]> = {};
  for (const match of source.matchAll(/type\s+(Public[A-Za-z]+)\s+struct\s+\{([\s\S]*?)\n\}/g)) {
    const structName = match[1];
    const body = match[2];
    fields[structName] = [...body.matchAll(/`json:"([^",]+)[^"]*"`/g)]
      .map((tag) => tag[1])
      .filter((field) => field !== "-");
  }
  return fields;
}

describe("protocol source alignment", () => {
  it("keeps frontend send action kinds aligned with backend OutboxKind constants and OpenAPI enum", async () => {
    const [eventsSource, openapiSource] = await Promise.all([
      readRepoText("internal/bridge/events.go"),
      readRepoText("internal/bridge/openapi.go")
    ]);

    const frontendKinds = [...SEND_ACTION_KINDS];
    const backendKinds = extractGoOutboxKinds(eventsSource);
    const openapiKinds = extractOpenAPISendActionKinds(openapiSource);

    expect(new Set(frontendKinds)).toEqual(new Set(backendKinds));
    expect(new Set(frontendKinds)).toEqual(new Set(openapiKinds));
    expect(openapiKinds).toEqual(frontendKinds);
  });

  it("keeps public message routes mapped to the intended internal action kinds", async () => {
    const [eventsSource, httpSource] = await Promise.all([
      readRepoText("internal/bridge/events.go"),
      readRepoText("internal/bridge/http.go")
    ]);

    const kindNames = extractGoOutboxKindNames(eventsSource);
    const routeMap = Object.fromEntries(
      extractPublicMessageRoutes(httpSource).map((route) => [route.pathKind, kindNames[route.goKind]])
    );

    expect(Object.keys(routeMap)).toHaveLength(SEND_ACTION_KINDS.length);
    expect(routeMap).toEqual({
      text: "text",
      image: "image",
      video: "video",
      voice: "voice",
      file: "file",
      emoji: "emoji",
      location: "location",
      quote: "quote",
      link: "link",
      "mini-program": "mini_program",
      "chat-history": "chat_history",
      revoke: "revoke"
    });
    expect(Object.values(routeMap)).toEqual([...SEND_ACTION_KINDS]);
  });

  it("keeps public capabilities send endpoints aligned with route and frontend action kinds", async () => {
    const [eventsSource, httpSource, publicAPISource] = await Promise.all([
      readRepoText("internal/bridge/events.go"),
      readRepoText("internal/bridge/http.go"),
      readRepoText("internal/bridge/public_api.go")
    ]);

    const kindNames = extractGoOutboxKindNames(eventsSource);
    const routeMap = Object.fromEntries(
      extractPublicMessageRoutes(httpSource).map((route) => [`/api/v1/messages/${route.pathKind}`, kindNames[route.goKind]])
    );
    const capabilityMap = Object.fromEntries(
      extractCapabilitySendMappings(publicAPISource).map((capability) => [capability.endpoint, kindNames[capability.goKind]])
    );

    expect(Object.keys(capabilityMap)).toHaveLength(SEND_ACTION_KINDS.length);
    expect(capabilityMap).toEqual(routeMap);
    expect(Object.values(capabilityMap)).toEqual([...SEND_ACTION_KINDS]);
  });

  it("keeps documented public send endpoints aligned with registered routes", async () => {
    const [httpSource, openapiSource] = await Promise.all([
      readRepoText("internal/bridge/http.go"),
      readRepoText("internal/bridge/openapi.go")
    ]);
    const spec = extractOpenAPIJSON(openapiSource);
    const routePaths = extractPublicMessageRoutes(httpSource).map((route) => `/api/v1/messages/${route.pathKind}`);

    expect(routePaths).toHaveLength(SEND_ACTION_KINDS.length);
    for (const path of [...routePaths, "/api/v1/messages/action"]) {
      const operation = spec.paths[path]?.post as { responses?: unknown } | undefined;
      expect(operation, `${path} is registered but missing from OpenAPI`).toBeDefined();
      const responseRefs = expandResponseRefs(spec, collectRefs(operation?.responses));
      expect(responseRefs, `${path} must return the public send response schema`).toContain(
        "#/components/schemas/PublicSendResponse"
      );
      expect(responseRefs, `${path} must not return the legacy send response schema`).not.toContain(
        "#/components/schemas/SendActionResponse"
      );
    }
  });

  it("keeps public protocol response structs free of internal and sensitive fields", async () => {
    const publicAPISource = await readRepoText("internal/bridge/public_api.go");
    const publicFields = extractPublicStructJSONFields(publicAPISource);
    const forbiddenFields = [
      "api_key",
      "raw_xml",
      "media_base64",
      "payload_json",
      "raw_provider",
      "password",
      "token",
      "secret",
      "cookie",
      "session",
      "auth",
      "authorization"
    ];

    expect(Object.keys(publicFields).length).toBeGreaterThan(0);
    for (const [structName, fields] of Object.entries(publicFields)) {
      expect(fields, `${structName} exposes an internal or sensitive json field`).not.toEqual(
        expect.arrayContaining(forbiddenFields)
      );
    }
  });

  it("keeps /api/v1 OpenAPI responses on public schemas instead of internal event schemas", async () => {
    const openapiSource = await readRepoText("internal/bridge/openapi.go");
    const spec = extractOpenAPIJSON(openapiSource);
    const publicPaths = Object.entries(spec.paths).filter(([path]) => path.startsWith("/api/v1/"));
    const refsByPath = Object.fromEntries(
      publicPaths.map(([path, methods]) => {
        const refs = Object.values(methods).flatMap((operation) =>
          expandResponseRefs(spec, collectRefs((operation as { responses?: unknown }).responses))
        );
        return [path, refs];
      })
    );

    expect(publicPaths.length).toBeGreaterThan(0);
    for (const [path, refs] of Object.entries(refsByPath)) {
      expect(refs, `${path} references the internal MessageEvent schema`).not.toContain("#/components/schemas/MessageEvent");
    }
    expect(refsByPath["/api/v1/messages"]).toContain("#/components/schemas/PublicMessageEnvelope");
    expect(refsByPath["/api/v1/outbox/{id}"]).toContain("#/components/schemas/PublicOutboxEnvelope");
  });
});
