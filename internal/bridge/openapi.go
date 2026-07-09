package bridge

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

const openAPIDocsHTML = `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>微信观测站 API 协议</title>
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
  <style>
    :root {
      color-scheme: light;
      --page: #f7f8f6;
      --panel: #ffffff;
      --panel-soft: #f1f5f2;
      --ink: #17201b;
      --muted: #5f6b64;
      --line: #d8ded9;
      --green: #167a64;
      --green-soft: #e4f3ed;
      --blue: #315b82;
      --blue-soft: #e7eef6;
      --amber: #9a5b18;
      --amber-soft: #f8eddb;
      --red: #a33f3f;
      --red-soft: #f7e7e4;
      --code: #101418;
      --code-line: #2a3036;
    }
    * { box-sizing: border-box; }
    html { scroll-behavior: smooth; }
    body {
      margin: 0;
      background: var(--page);
      color: var(--ink);
      font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", ui-sans-serif, system-ui, sans-serif;
      font-size: 15px;
      line-height: 1.65;
    }
    a { color: var(--blue); text-decoration-thickness: 1px; text-underline-offset: 3px; }
    a:hover { color: var(--green); }
    code {
      padding: 1px 4px;
      border: 1px solid #dfe4df;
      border-radius: 4px;
      background: #f7faf8;
      font-family: "Cascadia Mono", "SFMono-Regular", Consolas, "Liberation Mono", monospace;
      font-size: 13px;
    }
    pre {
      overflow: auto;
      margin: 0;
      padding: 16px;
      border-radius: 8px;
      background: var(--code);
      color: #e6ebed;
      line-height: 1.55;
      border: 1px solid var(--code-line);
    }
    pre code {
      padding: 0;
      border: 0;
      background: transparent;
      color: inherit;
      font-size: 13px;
    }
    .topbar {
      position: sticky;
      top: 0;
      z-index: 40;
      border-bottom: 1px solid var(--line);
      background: rgba(247, 248, 246, 0.94);
      backdrop-filter: blur(12px);
    }
    .topbar-inner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      max-width: 1440px;
      margin: 0 auto;
      padding: 12px 22px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 0;
      color: var(--ink);
      text-decoration: none;
    }
    .mark {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: 7px;
      background: var(--green);
      color: #fff;
      font-weight: 800;
    }
    .brand-title {
      display: block;
      font-size: 15px;
      font-weight: 750;
      line-height: 1.2;
      white-space: nowrap;
    }
    .brand-subtitle {
      display: block;
      color: var(--muted);
      font-size: 12px;
      line-height: 1.2;
      white-space: nowrap;
    }
    .top-actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
    }
    .link-button {
      display: inline-flex;
      align-items: center;
      min-height: 34px;
      padding: 0 11px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: var(--panel);
      color: var(--ink);
      text-decoration: none;
      font-size: 13px;
      font-weight: 700;
      white-space: nowrap;
    }
    .link-button.primary {
      border-color: var(--green);
      background: var(--green);
      color: #fff;
    }
    .layout {
      display: grid;
      grid-template-columns: 240px minmax(0, 1fr);
      gap: 26px;
      max-width: 1440px;
      margin: 0 auto;
      padding: 24px 22px 64px;
    }
    .side {
      position: sticky;
      top: 72px;
      align-self: start;
      padding: 14px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
    }
    .side-title {
      margin: 0 0 10px;
      color: var(--muted);
      font-size: 12px;
      font-weight: 800;
    }
    .side a {
      display: block;
      padding: 7px 8px;
      border-radius: 6px;
      color: var(--ink);
      text-decoration: none;
      font-size: 13px;
    }
    .side a:hover { background: var(--panel-soft); }
    .main {
      min-width: 0;
      display: grid;
      gap: 18px;
    }
    .hero {
      display: grid;
      grid-template-columns: minmax(0, 1fr) 330px;
      gap: 20px;
      align-items: stretch;
      padding: 26px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
    }
    .eyebrow {
      margin: 0 0 8px;
      color: var(--green);
      font-size: 13px;
      font-weight: 800;
    }
    h1 {
      margin: 0;
      font-size: 34px;
      line-height: 1.16;
      letter-spacing: 0;
    }
    .lead {
      max-width: 780px;
      margin: 13px 0 0;
      color: var(--muted);
      font-size: 16px;
      line-height: 1.75;
    }
    .summary-panel {
      display: grid;
      gap: 10px;
      align-content: start;
      border-left: 1px solid var(--line);
      padding-left: 20px;
    }
    .metric {
      display: grid;
      gap: 2px;
      padding: 10px 0;
      border-bottom: 1px solid var(--line);
    }
    .metric:last-child { border-bottom: 0; }
    .metric strong {
      font-size: 22px;
      line-height: 1.1;
    }
    .metric span {
      color: var(--muted);
      font-size: 13px;
    }
    .section {
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
      overflow: hidden;
    }
    .section-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
      padding: 18px 20px;
      border-bottom: 1px solid var(--line);
      background: #fbfcfb;
    }
    .section h2 {
      margin: 0;
      font-size: 21px;
      line-height: 1.25;
      letter-spacing: 0;
    }
    .section-desc {
      max-width: 720px;
      margin: 6px 0 0;
      color: var(--muted);
      font-size: 14px;
    }
    .section-body { padding: 20px; }
    .steps {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 12px;
    }
    .step {
      min-height: 170px;
      padding: 15px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: #fff;
    }
    .step-index {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 26px;
      height: 26px;
      margin-bottom: 12px;
      border-radius: 6px;
      background: var(--blue-soft);
      color: var(--blue);
      font-weight: 800;
    }
    .step h3,
    .doc-item h3,
    .field-card h3 {
      margin: 0 0 8px;
      font-size: 16px;
      line-height: 1.3;
      letter-spacing: 0;
    }
    .step p,
    .doc-item p,
    .field-card p {
      margin: 0;
      color: var(--muted);
      font-size: 14px;
      line-height: 1.65;
    }
    .endpoint-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }
    .endpoint {
      display: grid;
      gap: 5px;
      padding: 12px 13px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: #fff;
    }
    .endpoint code {
      width: fit-content;
      max-width: 100%;
      overflow-wrap: anywhere;
    }
    .method {
      color: var(--green);
      font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
      font-size: 12px;
      font-weight: 900;
    }
    .endpoint span:last-child {
      color: var(--muted);
      font-size: 13px;
    }
    .field-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 10px;
    }
    .field-card {
      min-height: 128px;
      padding: 14px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: #fff;
    }
    .field-card strong {
      display: block;
      margin-bottom: 5px;
      color: var(--green);
      font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
      font-size: 13px;
    }
    .callout {
      padding: 13px 14px;
      border: 1px solid #c9ded6;
      border-left: 4px solid var(--green);
      border-radius: 8px;
      background: var(--green-soft);
      color: #24463b;
      line-height: 1.7;
    }
    .callout.warning {
      border-color: #ead1a8;
      border-left-color: var(--amber);
      background: var(--amber-soft);
      color: #553916;
    }
    .matrix-wrap { overflow-x: auto; }
    .matrix {
      width: 100%;
      min-width: 900px;
      border-collapse: collapse;
      font-size: 14px;
    }
    .matrix th,
    .matrix td {
      padding: 11px 12px;
      border-bottom: 1px solid var(--line);
      text-align: left;
      vertical-align: top;
      line-height: 1.55;
    }
    .matrix th {
      background: var(--panel-soft);
      color: #26322c;
      font-size: 13px;
      font-weight: 800;
    }
    .matrix tr:last-child td { border-bottom: 0; }
    .status {
      display: inline-flex;
      align-items: center;
      min-height: 24px;
      padding: 0 8px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 800;
      white-space: nowrap;
    }
    .status.stable { background: var(--green-soft); color: var(--green); }
    .status.partial { background: var(--amber-soft); color: var(--amber); }
    .status.readonly { background: var(--blue-soft); color: var(--blue); }
    .status.stop { background: var(--red-soft); color: var(--red); }
    .doc-grid {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 10px;
    }
    .doc-item {
      min-height: 132px;
      padding: 14px;
      border: 1px solid var(--line);
      border-radius: 8px;
      background: #fff;
    }
    .doc-item a {
      color: var(--ink);
      font-weight: 800;
    }
    .split {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      gap: 14px;
      align-items: start;
    }
    .swagger-panel {
      border: 1px solid var(--line);
      border-radius: 8px;
      background: #fff;
      overflow: hidden;
    }
    .swagger-ui .topbar { display: none; }
    .swagger-ui { color: var(--ink); font-family: inherit; }
    .swagger-ui .info { margin: 18px 0; }
    .swagger-ui .scheme-container {
      box-shadow: none;
      border-top: 1px solid var(--line);
      border-bottom: 1px solid var(--line);
    }
    .swagger-ui .opblock,
    .swagger-ui .model-box,
    .swagger-ui .responses-wrapper {
      border-radius: 8px;
    }
    .mobile-nav {
      display: none;
      padding: 10px 22px 0;
    }
    .mobile-nav select {
      width: 100%;
      min-height: 38px;
      border: 1px solid var(--line);
      border-radius: 6px;
      background: var(--panel);
      color: var(--ink);
      font: inherit;
    }
    @media (max-width: 1080px) {
      .layout { grid-template-columns: 1fr; }
      .side { display: none; }
      .mobile-nav { display: block; }
      .hero { grid-template-columns: 1fr; }
      .summary-panel {
        grid-template-columns: repeat(3, minmax(0, 1fr));
        border-left: 0;
        border-top: 1px solid var(--line);
        padding-left: 0;
        padding-top: 14px;
      }
      .metric { border-bottom: 0; }
      .field-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }
    @media (max-width: 760px) {
      .topbar-inner {
        align-items: flex-start;
        flex-direction: column;
        padding: 11px 14px;
      }
      .top-actions { justify-content: flex-start; }
      .layout { padding: 16px 12px 44px; }
      .mobile-nav { padding: 10px 12px 0; }
      .hero,
      .section-head,
      .section-body { padding: 16px; }
      h1 { font-size: 28px; }
      .lead { font-size: 15px; }
      .steps,
      .endpoint-grid,
      .doc-grid,
      .split,
      .field-grid,
      .summary-panel { grid-template-columns: 1fr; }
      .brand-subtitle { white-space: normal; }
    }
  </style>
</head>
<body>
  <header class="topbar">
    <div class="topbar-inner">
      <a class="brand" href="/docs">
        <span class="mark">观</span>
        <span>
          <span class="brand-title">微信观测站 API 协议</span>
          <span class="brand-subtitle">Public API v1 · 手机模块收发协议面</span>
        </span>
      </a>
      <nav class="top-actions" aria-label="快捷入口">
        <a class="link-button primary" href="#quickstart">从这里开始</a>
        <a class="link-button" href="#capabilities">能力矩阵</a>
        <a class="link-button" href="#swagger-ui">接口详情</a>
        <a class="link-button" href="/admin">管理后台</a>
      </nav>
    </div>
  </header>

  <div class="mobile-nav">
    <select aria-label="页面目录" onchange="if (this.value) location.hash = this.value">
      <option value="">页面目录</option>
      <option value="overview">协议概览</option>
      <option value="quickstart">三步接入</option>
      <option value="envelope">消息信封</option>
      <option value="capabilities">能力矩阵</option>
      <option value="send-ack">发送与回执</option>
      <option value="docs-map">文档地图</option>
      <option value="openapi">接口详情</option>
    </select>
  </div>

  <div class="layout">
    <aside class="side" aria-label="页面目录">
      <p class="side-title">页面目录</p>
      <a href="#overview">协议概览</a>
      <a href="#quickstart">三步接入</a>
      <a href="#envelope">消息信封</a>
      <a href="#capabilities">能力矩阵</a>
      <a href="#send-ack">发送与回执</a>
      <a href="#docs-map">文档地图</a>
      <a href="#openapi">接口详情</a>
    </aside>

    <main class="main">
      <section id="overview" class="hero">
        <div>
          <p class="eyebrow">给外部机器人框架、插件和业务脚本看的接入面</p>
          <h1>把手机里的微信收发能力，整理成一个稳定协议。</h1>
          <p class="lead">
            手机模块负责真实微信收发，网关负责鉴权、消息信封、媒体文件、WebSocket 推送和 outbox 回执。
            外部系统只需要接 <code>/api/v1</code>，发送目标始终使用稳定 <code>wxid</code> 或群 <code>room id</code>，
            不依赖昵称、备注或群名。
          </p>
        </div>
        <div class="summary-panel" aria-label="当前协议状态">
          <div class="metric"><strong>v1</strong><span>公开协议版本</span></div>
          <div class="metric"><strong>11 类</strong><span>主要消息能力已覆盖</span></div>
          <div class="metric"><strong>WS + HTTP</strong><span>实时订阅和补拉并存</span></div>
        </div>
      </section>

      <section id="quickstart" class="section">
        <div class="section-head">
          <div>
            <h2>三步接入：外部适配快速路径</h2>
            <p class="section-desc">先发现能力，再补拉消息，最后订阅实时事件。发送侧只看 outbox 终态，不把入队当成发送成功。</p>
          </div>
        </div>
        <div class="section-body">
          <div class="steps">
            <article class="step">
              <span class="step-index">1</span>
              <h3>能力发现</h3>
              <p>调用 <code>GET /api/v1/capabilities</code>，读取可收、可发、样本可用和只读识别的消息类型。</p>
            </article>
            <article class="step">
              <span class="step-index">2</span>
              <h3>补拉历史</h3>
              <p>用 <code>GET /api/v1/messages?after_id=0&amp;limit=100</code> 获取统一信封，保存最后处理成功的消息 ID。</p>
            </article>
            <article class="step">
              <span class="step-index">3</span>
              <h3>订阅实时</h3>
              <p>连接 <code>GET /api/v1/ws</code>。断线后从最后 ID 继续补拉，再重建 WebSocket。</p>
            </article>
          </div>
          <div class="callout" style="margin-top: 12px;">
            外部鉴权优先使用 <code>X-Bridge-API-Key</code>。WebSocket 不方便带 header 时，可以用 <code>api_key</code> 查询参数。
          </div>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <h2>常用入口</h2>
            <p class="section-desc">普通接入尽量使用具体类型接口；高级统一入口保留给复杂场景和内部调试。</p>
          </div>
        </div>
        <div class="section-body">
          <div class="endpoint-grid">
            <div class="endpoint"><span class="method">GET</span><code>/api/v1/capabilities</code><span>查询协议能力</span></div>
            <div class="endpoint"><span class="method">GET</span><code>/api/v1/messages</code><span>补拉或查询消息信封</span></div>
            <div class="endpoint"><span class="method">GET</span><code>/api/v1/ws</code><span>实时消息 WebSocket</span></div>
            <div class="endpoint"><span class="method">GET</span><code>/api/v1/outbox/{id}</code><span>查询发送任务状态</span></div>
            <div class="endpoint"><span class="method">POST</span><code>/api/v1/messages/text</code><span>发送文本</span></div>
            <div class="endpoint"><span class="method">POST</span><code>/api/v1/messages/image</code><span>发送图片</span></div>
            <div class="endpoint"><span class="method">POST</span><code>/api/v1/messages/video</code><span>发送视频</span></div>
            <div class="endpoint"><span class="method">POST</span><code>/api/v1/messages/action</code><span>高级统一发送</span></div>
          </div>
        </div>
      </section>

      <section id="envelope" class="section">
        <div class="section-head">
          <div>
            <h2>MessageEnvelope v1</h2>
            <p class="section-desc">所有入站消息、历史补拉和 WebSocket 推送都使用同一个信封。适配器优先按这些稳定字段解析。</p>
          </div>
        </div>
        <div class="section-body">
          <div class="field-grid">
            <article class="field-card"><strong>kind</strong><h3>稳定类型</h3><p>例如 <code>text</code>、<code>image</code>、<code>appmsg</code>、<code>emoji</code>、<code>payment</code>。</p></article>
            <article class="field-card"><strong>chat_id</strong><h3>会话 ID</h3><p>私聊是好友 wxid，群聊是 room id。发送和过滤都不要传显示名。</p></article>
            <article class="field-card"><strong>media[]</strong><h3>媒体资源</h3><p>包含 <code>kind</code>、<code>mime</code>、<code>name</code>、<code>url</code>、<code>size</code>、<code>opaque</code>。</p></article>
            <article class="field-card"><strong>appmsg</strong><h3>卡片结构</h3><p>链接、小程序、聊天记录、引用、文件等业务卡片放在这里。</p></article>
            <article class="field-card"><strong>location</strong><h3>位置结构</h3><p>经纬度、缩放、POI、地址文本等结构化位置字段。</p></article>
            <article class="field-card"><strong>evidence</strong><h3>字段证据</h3><p>记录字段来源，例如微信 message.type、XML 节点或解析路径。</p></article>
            <article class="field-card"><strong>unsupported</strong><h3>明确降级</h3><p>无法稳定映射的字段不会乱猜，会放入 unsupported 等待后续分类。</p></article>
            <article class="field-card"><strong>direction</strong><h3>消息方向</h3><p><code>recv</code> 或 <code>sent</code>，发送回执和真实入站消息都保持一致。</p></article>
          </div>
        </div>
      </section>

      <section id="capabilities" class="section">
        <div class="section-head">
          <div>
            <h2>能力矩阵</h2>
            <p class="section-desc">状态分为稳定、样本可用、源转发、只读识别和不支持。支付类只识别不发送。</p>
          </div>
        </div>
        <div class="section-body matrix-wrap">
          <table class="matrix">
            <thead>
              <tr>
                <th>消息类型</th>
                <th>发送入口</th>
                <th>当前状态</th>
                <th>关键字段</th>
                <th>注意事项</th>
              </tr>
            </thead>
            <tbody>
              <tr><td>文本</td><td><code>/api/v1/messages/text</code></td><td><span class="status stable">稳定</span></td><td><code>text</code></td><td>旧接口仍兼容。</td></tr>
              <tr><td>图片</td><td><code>/api/v1/messages/image</code></td><td><span class="status stable">稳定</span></td><td><code>media_url</code> 或 <code>media_base64</code></td><td>媒体输出优先使用 URL。</td></tr>
              <tr><td>视频</td><td><code>/api/v1/messages/video</code></td><td><span class="status stable">稳定</span></td><td><code>media_url</code> 或 <code>media_base64</code></td><td>ACK 以真实微信消息记录为准。</td></tr>
              <tr><td>语音</td><td><code>/api/v1/messages/voice</code></td><td><span class="status stable">可用</span></td><td><code>media_url</code> 或 <code>media_base64</code></td><td>继续积累 AMR/SILK 时长样本。</td></tr>
              <tr><td>文件</td><td><code>/api/v1/messages/file</code></td><td><span class="status stable">稳定</span></td><td><code>media_name</code>、<code>media_url</code></td><td>建议始终提供文件名。</td></tr>
              <tr><td>表情</td><td><code>/api/v1/messages/emoji</code></td><td><span class="status stable">结构化</span></td><td><code>emoji_md5</code> 或 <code>source_chat_record_id</code></td><td>本地文件可能是 opaque，优先看 MD5/CDN。</td></tr>
              <tr><td>位置</td><td><code>/api/v1/messages/location</code></td><td><span class="status stable">稳定</span></td><td><code>location_latitude</code>、<code>location_longitude</code></td><td>服务端校验经纬度范围。</td></tr>
              <tr><td>引用</td><td><code>/api/v1/messages/quote</code></td><td><span class="status partial">样本可用</span></td><td><code>text</code>、<code>quote_msg_id</code></td><td>群聊引用仍需要更多样本。</td></tr>
              <tr><td>链接</td><td><code>/api/v1/messages/link</code></td><td><span class="status stable">稳定</span></td><td><code>source_chat_record_id</code> 或标题 + URL</td><td>可原样转发，也可直接构造。</td></tr>
              <tr><td>小程序</td><td><code>/api/v1/messages/mini-program</code></td><td><span class="status partial">源转发稳定</span></td><td><code>source_chat_record_id</code> 或 username + page path</td><td>直接构造还需要真实样本。</td></tr>
              <tr><td>聊天记录</td><td><code>/api/v1/messages/chat-history</code></td><td><span class="status partial">转发限定</span></td><td><code>source_chat_record_ids</code>、<code>recorditem_xml</code></td><td>不开放任意 raw XML 自动化。</td></tr>
              <tr><td>撤回</td><td><code>/api/v1/messages/revoke</code></td><td><span class="status partial">私聊已验证</span></td><td><code>target_chat_record_id</code></td><td>群聊跟随撤回仍需继续积累样本。</td></tr>
              <tr><td>支付/红包/转账</td><td>无</td><td><span class="status readonly">只读识别</span></td><td><code>kind=payment</code>、<code>subtype</code></td><td>出站自动化明确不支持。</td></tr>
              <tr><td>未知业务类型</td><td>无</td><td><span class="status stop">保留证据</span></td><td><code>message_type</code>、<code>unsupported</code></td><td>确认前不猜测业务含义。</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section id="send-ack" class="section">
        <div class="section-head">
          <div>
            <h2>发送与回执</h2>
            <p class="section-desc">发送接口返回只代表任务入队。真正的结果来自手机模块 ACK 和本地微信消息记录。</p>
          </div>
        </div>
        <div class="section-body">
          <div class="split">
            <pre><code>POST /api/v1/messages/text
X-Bridge-API-Key: &lt;your-api-key&gt;

{
  "wx_ids": ["&lt;target_wxid_or_room_id&gt;"],
  "text": "测试文本"
}</code></pre>
            <pre><code>GET /api/v1/outbox/1001
X-Bridge-API-Key: &lt;your-api-key&gt;

{
  "ok": true,
  "outbox": {
    "id": 1001,
    "kind": "text",
    "status": "sent"
  }
}</code></pre>
          </div>
          <div class="callout warning" style="margin-top: 12px;">
            安全约定：不要把真实 <code>wxid</code>、API key、密码、cookie、token、聊天内容或媒体 base64 写入日志、文档和公开示例。
          </div>
        </div>
      </section>

      <section id="docs-map" class="section">
        <div class="section-head">
          <div>
            <h2>文档地图</h2>
            <p class="section-desc">先看快速接入和样例；遇到边界问题再看错误码、证据和稳定性评审。</p>
          </div>
        </div>
        <div class="section-body">
          <div class="doc-grid">
            <article class="doc-item"><h3><a href="/docs/adapter-quickstart-v1.md">Adapter Quickstart 快速接入</a></h3><p>能力发现、cursor 补拉、WebSocket 实时消息、发送 ACK 和安全规则。</p></article>
            <article class="doc-item"><h3><a href="/docs/public-api-message-samples-v1.md">Public API Message Samples 消息样例</a></h3><p>各消息类型的标准 JSON 信封、发送请求和 ACK 样例。</p></article>
            <article class="doc-item"><h3><a href="/docs/public-api-errors-v1.md">Public API Errors 错误码</a></h3><p>错误结构、重试策略、权限边界和 outbox 终态语义。</p></article>
            <article class="doc-item"><h3><a href="/docs/public-api-python-client-v1.md">Public API Python Client</a></h3><p>适合机器人框架或业务脚本直接接入的最小封装。</p></article>
            <article class="doc-item"><h3><a href="/docs/capability-evidence-v1.md">能力证据</a></h3><p>每个消息能力的真实样本、当前状态和升级缺口。</p></article>
            <article class="doc-item"><h3><a href="/docs/protocol-stability-review-v1.md">稳定性评审</a></h3><p>稳定字段、兼容边界、媒体读取和后续升级原则。</p></article>
            <article class="doc-item"><h3><a href="/docs/protocol-capability-review-2026-07-03.md">当前能力评审</a></h3><p>基于真实数据库和公开 API 的阶段性协议收口报告。</p></article>
            <article class="doc-item"><h3><a href="/docs/openapi.json">OpenAPI JSON</a></h3><p>用于生成客户端、做接口校验，或同步到外部文档平台。</p></article>
          </div>
          <div class="callout" style="margin-top: 12px;">
            <code>/module/...</code> 是手机模块内部协议，<code>/api/live/events</code> 是管理台兼容通道。外部机器人和业务系统优先接 <code>/api/v1</code>。
          </div>
        </div>
      </section>

      <section id="openapi" class="section">
        <div class="section-head">
          <div>
            <h2>接口详情</h2>
            <p class="section-desc">下面是完整 OpenAPI 交互文档。用于查参数、响应结构和错误返回。</p>
          </div>
          <a class="link-button" href="/docs/openapi.json">下载 JSON</a>
        </div>
        <div class="section-body">
          <div class="swagger-panel">
            <div id="swagger-ui"></div>
          </div>
        </div>
      </section>
    </main>
  </div>

  <noscript>
    交互式接口文档需要启用 JavaScript。OpenAPI JSON 地址：
    <a href="/docs/openapi.json">/docs/openapi.json</a>。
  </noscript>
  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
  <script>
    window.ui = SwaggerUIBundle({
      url: "/docs/openapi.json",
      dom_id: "#swagger-ui",
      deepLinking: true,
      presets: [SwaggerUIBundle.presets.apis],
      layout: "BaseLayout"
    });
  </script>
</body>
</html>
`

const openAPIJSONDocument = `{
  "openapi": "3.0.3",
  "info": {
    "title": "微信观测站 API 协议",
    "version": "1.0.0",
    "description": "Go 网关和 Android 模块之间的公开协议面。外部接入优先使用 X-Bridge-API-Key；发送目标必须使用稳定 wxid 或群 room id；昵称、备注、群名只作为展示字段。"
  },
  "servers": [
    {
      "url": "/",
      "description": "当前网关"
    }
  ],
  "tags": [
    {
      "name": "外部发送",
      "description": "外部系统调用的消息发送接口，底层由 Action Outbox 和手机模块执行。"
    },
    {
      "name": "外部查询",
      "description": "消息、联系人、模块状态、媒体文件等只读查询接口。"
    },
    {
      "name": "兼容接口",
      "description": "早期管理后台接口和高级统一接口。新接入优先使用 /api/v1。"
    },
    {
      "name": "手机模块协议",
      "description": "Android 模块注册、消息上报、出站任务拉取和 ACK 回写接口。"
    }
  ],
  "paths": {
    "/api/v1/messages/text": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送文本",
        "description": "最简单的出站接口。目标必须传 wxid 或群 room id，不传昵称。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "$ref": "#/components/schemas/TextMessageRequest" },
              "example": {
                "wx_ids": ["<target_wxid_or_room_id>"],
                "text": "这是一条测试文本"
              }
            }
          }
        },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/image": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送图片",
        "description": "图片可使用已有 media_url，也可提交 media_base64 让网关保存成媒体文件。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/MediaMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/video": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送视频",
        "description": "视频真实发送结果以手机模块 ACK 为准。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/MediaMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/voice": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送语音",
        "description": "语音使用媒体字段入队；发送已通过 DB 观测验证，播放时长和更多 AMR/SILK 样本仍需扩展。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/MediaMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/file": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送文件",
        "description": "文件发送需要媒体内容，建议提供 media_name 作为对方看到的文件名。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/MediaMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/emoji": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送表情",
        "description": "可按 emoji_md5 发送，也可引用已有消息 source_chat_record_id 原样转发。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/EmojiMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/location": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送位置",
        "description": "经纬度为必填字段，范围会在服务端校验。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/LocationMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/quote": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送引用回复",
        "description": "需要引用目标消息 ID，并填写回复文本；当前属于样本可用，群聊引用仍需更多验证。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/QuoteMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/link": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送链接卡片",
        "description": "普通网页链接卡片；可用 source_chat_record_id 原样转发，也可用标题和 URL 直接构造。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/LinkMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/mini-program": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送小程序卡片",
        "description": "小程序卡片支持 source_chat_record_id 原样转发；直接构造需要小程序 username 和页面路径，仍需更多真实样本。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/MiniProgramMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/chat-history": {
      "post": {
        "tags": ["外部发送"],
        "summary": "发送聊天记录",
        "description": "可传 recorditem_xml 或 source_chat_record_ids 构造聊天记录；当前以已有来源消息转发为主，不提供任意 raw XML 自动化。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ChatHistoryMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/revoke": {
      "post": {
        "tags": ["外部发送"],
        "summary": "撤回已发送消息",
        "description": "撤回当前账号已发送且本地 message 表仍可查到的消息。target_chat_record_id 应来自发送 ACK 返回的 chat_record_id。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/RevokeMessageRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages/action": {
      "post": {
        "tags": ["外部发送"],
        "summary": "高级统一发送",
        "description": "高级入口，直接提交 kind 和完整 action 字段。普通接入优先使用具体类型接口。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": { "required": true, "content": { "application/json": { "schema": { "$ref": "#/components/schemas/SendActionRequest" } } } },
        "responses": {
          "200": { "$ref": "#/components/responses/SendQueuedResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/capabilities": {
      "get": {
        "tags": ["外部查询"],
        "summary": "查询协议能力",
        "description": "返回 MessageEnvelope v1 字段、消息类型能力矩阵、传输通道和安全限制。外部适配器应优先读取该接口判断可用能力。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "responses": {
          "200": {
            "description": "协议能力清单",
            "content": { "application/json": { "schema": { "$ref": "#/components/schemas/CapabilitiesResponse" } } }
          },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/messages": {
      "get": {
        "tags": ["外部查询"],
        "summary": "查询已存消息",
        "description": "查询收发消息记录。群聊、私聊都使用稳定 chat_id/wxid 过滤。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "parameters": [
          { "name": "device", "in": "query", "schema": { "type": "string" } },
          { "name": "wxid", "in": "query", "schema": { "type": "string" }, "description": "稳定的好友 wxid 或群 room id。" },
          { "name": "chat_kind", "in": "query", "schema": { "type": "string", "enum": ["direct", "room", "unknown"] } },
          { "name": "after_id", "in": "query", "schema": { "type": "integer", "format": "int64", "minimum": 0 }, "description": "补拉大于该消息 id 的新消息，按 id 升序返回。" },
          { "name": "before_id", "in": "query", "schema": { "type": "integer", "format": "int64", "minimum": 0 }, "description": "翻页查询小于该消息 id 的旧消息，按 id 倒序返回。" },
          { "name": "limit", "in": "query", "schema": { "type": "integer", "default": 100, "maximum": 500 } }
        ],
        "responses": {
          "200": { "$ref": "#/components/responses/MessageListResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/outbox/{id}": {
      "get": {
        "tags": ["外部查询"],
        "summary": "查询发送任务状态",
        "description": "发送接口返回 outbox_id 后，可通过该接口查询 queued/pending、leased、sent、failed 等状态和失败原因。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "parameters": [
          {
            "name": "id",
            "in": "path",
            "required": true,
            "schema": { "type": "integer", "format": "int64" },
            "description": "发送接口返回的 outbox_id。"
          }
        ],
        "responses": {
          "200": { "$ref": "#/components/responses/OutboxItemResponse" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" },
          "404": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/ws": {
      "get": {
        "tags": ["外部查询"],
        "summary": "实时消息 WebSocket",
        "description": "外部机器人框架推荐使用的实时消息通道。连接后服务端发送 hello；收到微信消息时推送 type=message。可用 replay 查询参数或发送 {\"type\":\"replay\",\"limit\":20} 补拉最近事件。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "parameters": [
          {
            "name": "replay",
            "in": "query",
            "schema": { "type": "integer", "minimum": 0, "maximum": 200 },
            "description": "连接成功后补发最近 N 条事件，默认 0。"
          }
        ],
        "responses": {
          "101": { "description": "WebSocket 升级成功" },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/contacts": {
      "get": {
        "tags": ["外部查询"],
        "summary": "查询联系人和群",
        "description": "联系人接口只用于展示、搜索和选择目标。真正发送时仍然传 wxid 或 room id。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "parameters": [
          { "name": "device", "in": "query", "schema": { "type": "string" } },
          { "name": "owner_wxid", "in": "query", "schema": { "type": "string" } },
          { "name": "q", "in": "query", "schema": { "type": "string" } },
          { "name": "include_deleted", "in": "query", "schema": { "type": "boolean" } },
          { "name": "limit", "in": "query", "schema": { "type": "integer", "default": 200, "maximum": 500 } }
        ],
        "responses": {
          "200": { "$ref": "#/components/responses/ContactListResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/v1/modules/status": {
      "get": {
        "tags": ["外部查询"],
        "summary": "查询手机模块状态",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "responses": {
          "200": { "$ref": "#/components/responses/ModuleStatusListResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/healthz": {
      "get": {
        "tags": ["外部查询"],
        "summary": "健康检查",
        "responses": {
          "200": {
            "description": "网关存活",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "ok": { "type": "boolean" }
                  }
                }
              }
            }
          }
        }
      }
    },
    "/api/send/text": {
      "post": {
        "tags": ["兼容接口"],
        "summary": "发送文本消息",
        "description": "兼容旧接口。内部会入队为 kind=text，保持现有文本发送路径稳定。",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "$ref": "#/components/schemas/SendTextRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "文本发送动作已入队",
            "content": {
              "application/json": {
                "schema": { "$ref": "#/components/schemas/SendActionResponse" }
              }
            }
          },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/send/action": {
      "post": {
        "tags": ["兼容接口"],
        "summary": "发送任意支持类型消息",
        "description": "核心出站协议。接口返回代表任务已入队；手机模块在微信内真实执行后，再 ACK sent 或 failed。",
        "security": [{ "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "$ref": "#/components/schemas/SendActionRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "发送动作已入队",
            "content": {
              "application/json": {
                "schema": { "$ref": "#/components/schemas/SendActionResponse" }
              }
            }
          },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/messages": {
      "get": {
        "tags": ["兼容接口"],
        "summary": "查询已存消息",
        "security": [{ "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "parameters": [
          { "name": "device", "in": "query", "schema": { "type": "string" } },
          { "name": "wxid", "in": "query", "schema": { "type": "string" }, "description": "稳定的好友 wxid 或群 room id。" },
          { "name": "chat_kind", "in": "query", "schema": { "type": "string", "enum": ["direct", "room", "unknown"] } },
          { "name": "limit", "in": "query", "schema": { "type": "integer", "default": 200, "maximum": 500 } }
        ],
        "responses": {
          "200": {
            "description": "已存消息列表",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "messages": {
                      "type": "array",
                      "items": { "$ref": "#/components/schemas/PublicMessageEnvelope" }
                    }
                  }
                }
              }
            }
          },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/module-contacts": {
      "get": {
        "tags": ["兼容接口"],
        "summary": "查询联系人和群显示名",
        "description": "联系人只提供展示和搜索元数据。发送接口仍然必须使用 wxid 或 room id，不要传昵称。",
        "security": [{ "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "parameters": [
          { "name": "device", "in": "query", "schema": { "type": "string" } },
          { "name": "owner_wxid", "in": "query", "schema": { "type": "string" } },
          { "name": "q", "in": "query", "schema": { "type": "string" } },
          { "name": "include_deleted", "in": "query", "schema": { "type": "boolean" } },
          { "name": "limit", "in": "query", "schema": { "type": "integer", "default": 200, "maximum": 500 } }
        ],
        "responses": {
          "200": {
            "description": "联系人列表",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "contacts": {
                      "type": "array",
                      "items": { "$ref": "#/components/schemas/ModuleContact" }
                    }
                  }
                }
              }
            }
          },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/modules/status": {
      "get": {
        "tags": ["兼容接口"],
        "summary": "查询手机模块状态",
        "security": [{ "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "responses": {
          "200": {
            "description": "模块状态列表",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "modules": {
                      "type": "array",
                      "items": { "$ref": "#/components/schemas/ModuleStatus" }
                    }
                  }
                }
              }
            }
          },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/media/{media_path}": {
      "get": {
        "tags": ["外部查询"],
        "summary": "读取已保存媒体文件",
        "security": [{ "BridgeAPIKeyHeader": [] }, { "BridgeAPIKeyQuery": [] }, { "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "parameters": [
          {
            "name": "media_path",
            "in": "path",
            "required": true,
            "schema": { "type": "string" },
            "description": "media_url 返回的相对路径，不包含 /api/media/ 前缀。"
          }
        ],
        "responses": {
          "200": {
            "description": "媒体文件字节流。外部 API Key 只能读取自己设备产生的媒体路径。",
            "content": {
              "application/octet-stream": {
                "schema": { "type": "string", "format": "binary" }
              }
            }
          },
          "401": { "$ref": "#/components/responses/ErrorResponse" },
          "403": { "$ref": "#/components/responses/ErrorResponse" },
          "404": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/api/live/events": {
      "get": {
        "tags": ["外部查询"],
        "summary": "实时消息 SSE 流",
        "security": [{ "BridgePasswordHeader": [] }, { "BridgePasswordQuery": [] }],
        "responses": {
          "200": {
            "description": "SSE 事件流",
            "content": {
              "text/event-stream": {
                "schema": { "type": "string" }
              }
            }
          },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/module/register": {
      "post": {
        "tags": ["手机模块协议"],
        "summary": "注册手机模块身份",
        "description": "手机模块用 api_key 绑定服务端 device 和当前登录微信 owner_wxid。",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "$ref": "#/components/schemas/ModuleRegistrationRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "注册结果",
            "content": {
              "application/json": {
                "schema": { "$ref": "#/components/schemas/ModuleRegistrationResult" }
              }
            }
          },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/module/outbox/poll": {
      "post": {
        "tags": ["手机模块协议"],
        "summary": "手机模块拉取出站任务",
        "description": "网关会串行化手机发送；当前实现一次只租约一个待发送动作。",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "$ref": "#/components/schemas/ModulePollRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "出站任务列表",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "items": {
                      "type": "array",
                      "items": { "$ref": "#/components/schemas/ModuleOutboxItem" }
                    }
                  }
                }
              }
            }
          },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/module/outbox/ack": {
      "post": {
        "tags": ["手机模块协议"],
        "summary": "手机模块回写发送结果",
        "description": "ACK 状态只能是 sent 或 failed。sent ACK 会被写回为出站消息事件。",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "$ref": "#/components/schemas/ModuleAckRequest" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "已 ACK 的任务列表",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "items": {
                      "type": "array",
                      "items": { "$ref": "#/components/schemas/ModuleOutboxItem" }
                    }
                  }
                }
              }
            }
          },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/module/outbox/ws": {
      "get": {
        "tags": ["手机模块协议"],
        "summary": "手机模块 WebSocket 出站通道",
        "description": "手机模块用 api_key、device、wxid 查询参数连接。服务端推送 wake/outbox 消息，模块回传 ACK 消息。",
        "parameters": [
          { "name": "api_key", "in": "query", "required": true, "schema": { "type": "string" } },
          { "name": "device", "in": "query", "required": true, "schema": { "type": "string" } },
          { "name": "wxid", "in": "query", "schema": { "type": "string" } }
        ],
        "responses": {
          "101": { "description": "WebSocket 升级成功" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    },
    "/webhook/module/message": {
      "post": {
        "tags": ["手机模块协议"],
        "summary": "手机模块上报消息事件",
        "description": "Android 模块上报标准化后的入站消息或已发送观测事件。这里可接收媒体 base64，并保存为 /api/media/ URL。",
        "requestBody": {
          "required": true,
          "content": {
            "application/json": {
              "schema": { "$ref": "#/components/schemas/MessageEvent" }
            }
          }
        },
        "responses": {
          "200": {
            "description": "上报结果",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object",
                  "properties": {
                    "ok": { "type": "boolean" },
                    "chat_record_id": { "type": "integer", "format": "int64" }
                  }
                }
              }
            }
          },
          "400": { "$ref": "#/components/responses/ErrorResponse" },
          "401": { "$ref": "#/components/responses/ErrorResponse" }
        }
      }
    }
  },
  "components": {
    "securitySchemes": {
      "BridgePasswordHeader": {
        "type": "apiKey",
        "in": "header",
        "name": "X-Bridge-Password"
      },
      "BridgePasswordQuery": {
        "type": "apiKey",
        "in": "query",
        "name": "password"
      },
      "BridgeAPIKeyHeader": {
        "type": "apiKey",
        "in": "header",
        "name": "X-Bridge-API-Key",
        "description": "外部系统调用 /api/v1 时推荐使用。API Key 会绑定到一台手机。"
      },
      "BridgeAPIKeyQuery": {
        "type": "apiKey",
        "in": "query",
        "name": "api_key",
        "description": "主要用于 WebSocket 或无法设置自定义 header 的客户端。"
      }
    },
    "responses": {
      "SendQueuedResponse": {
        "description": "发送动作已入队。是否真实发送成功请以后续 ACK 和消息记录为准。",
        "content": {
          "application/json": {
            "schema": { "$ref": "#/components/schemas/PublicSendResponse" },
            "examples": {
              "text_queued": {
                "summary": "文本消息已入队",
                "value": {
                  "ok": true,
	                  "protocol_version": "v1",
	                  "kind": "text",
	                  "outbox_id": 123,
	                  "status_url": "/api/v1/outbox/123",
	                  "outbox": {
                    "id": 123,
                    "device": "phone-a",
                    "target_wxid": "<target_wxid_or_room_id>",
                    "kind": "text",
                    "text": "这是一条测试文本",
                    "status": "pending",
                    "status_url": "/api/v1/outbox/123"
                  }
                }
              },
              "image_queued": {
                "summary": "图片消息已入队",
                "value": {
                  "ok": true,
	                  "protocol_version": "v1",
	                  "kind": "image",
	                  "outbox_id": 124,
	                  "status_url": "/api/v1/outbox/124",
	                  "outbox": {
                    "id": 124,
                    "device": "phone-a",
                    "target_wxid": "<target_wxid_or_room_id>",
                    "kind": "image",
                    "status": "pending",
                    "status_url": "/api/v1/outbox/124",
                    "media": [
                      { "kind": "image", "mime": "image/png", "name": "sample.png", "url": "/api/media/phone-a/2026/07/sample.png", "size": 1024 }
                    ]
                  }
                }
              }
            }
          }
        }
      },
      "MessageListResponse": {
        "description": "消息列表",
        "content": {
          "application/json": {
            "schema": {
              "type": "object",
              "properties": {
                "ok": { "type": "boolean", "example": true },
                "protocol_version": { "type": "string", "example": "v1" },
                "messages": {
                  "type": "array",
                  "items": { "$ref": "#/components/schemas/PublicMessageEnvelope" }
                },
                "next_cursor": { "type": "integer", "format": "int64" },
                "next_cursor_param": { "type": "string", "enum": ["after_id", "before_id"] },
                "cursor_field": { "type": "string", "example": "id" },
                "has_more": { "type": "boolean" }
              }
            },
            "examples": {
              "text_and_media": {
                "summary": "补拉消息返回统一信封",
                "value": {
                  "ok": true,
                  "protocol_version": "v1",
                  "messages": [
                    {
                      "id": "msg-1001",
                      "chat_record_id": 1001,
                      "device": "phone-a",
                      "owner_wxid": "<owner_wxid>",
                      "direction": "recv",
                      "kind": "image",
                      "message_type": 3,
                      "chat_id": "<chat_wxid_or_room_id>",
                      "chat_kind": "direct",
                      "from_wxid": "<sender_wxid>",
                      "to_wxid": "<owner_wxid>",
                      "media": [
                        { "kind": "image", "mime": "image/jpeg", "name": "image.jpg", "url": "/api/media/phone-a/2026/07/image.jpg", "size": 2048 }
                      ],
                      "evidence": ["message.type=3"],
                      "created_at": "2026-07-02T12:00:00Z"
                    }
                  ],
                  "next_cursor": 1001,
                  "next_cursor_param": "after_id",
                  "cursor_field": "id",
                  "has_more": false
                }
              }
            }
          }
        }
      },
      "ContactListResponse": {
        "description": "联系人列表",
        "content": {
          "application/json": {
            "schema": {
              "type": "object",
              "properties": {
                "contacts": {
                  "type": "array",
                  "items": { "$ref": "#/components/schemas/ModuleContact" }
                }
              }
            }
          }
        }
      },
      "ModuleStatusListResponse": {
        "description": "模块状态列表",
        "content": {
          "application/json": {
            "schema": {
              "type": "object",
              "properties": {
                "modules": {
                  "type": "array",
                  "items": { "$ref": "#/components/schemas/ModuleStatus" }
                }
              }
            }
          }
        }
      },
      "OutboxItemResponse": {
        "description": "发送任务状态",
        "content": {
          "application/json": {
            "schema": {
              "type": "object",
              "properties": {
                "ok": { "type": "boolean", "example": true },
                "protocol_version": { "type": "string", "example": "v1" },
                "outbox": { "$ref": "#/components/schemas/PublicOutboxEnvelope" }
              }
            },
            "examples": {
              "sent": {
                "summary": "手机模块已 ACK 成功",
                "value": {
                  "ok": true,
                  "protocol_version": "v1",
                  "outbox": {
                    "id": 123,
                    "device": "phone-a",
                    "target_wxid": "<target_wxid_or_room_id>",
                    "kind": "text",
                    "text": "这是一条测试文本",
                    "status": "sent",
                    "status_url": "/api/v1/outbox/123",
                    "chat_record_id": 123,
                    "attempt_count": 1
                  }
                }
              },
              "failed": {
                "summary": "手机模块 ACK 失败并返回原因",
                "value": {
                  "ok": true,
                  "protocol_version": "v1",
                  "outbox": {
                    "id": 125,
                    "device": "phone-a",
                    "target_wxid": "<target_wxid_or_room_id>",
                    "kind": "image",
                    "status": "failed",
                    "status_url": "/api/v1/outbox/125",
                    "attempt_count": 1,
                    "last_error": "media download failed"
                  }
                }
              }
            }
          }
        }
      },
      "ErrorResponse": {
        "description": "错误响应",
        "content": {
          "application/json": {
            "schema": { "$ref": "#/components/schemas/ErrorResponse" },
            "examples": {
              "owner_wxid_unbound": {
                "summary": "API Key 绑定设备尚未注册当前微信账号",
                "value": { "ok": false, "code": "owner_wxid_unbound", "message": "device current owner_wxid is not registered; wait for module registration" }
              },
              "media_forbidden": {
                "summary": "媒体路径不属于当前 API Key 绑定设备",
                "value": { "ok": false, "code": "media_forbidden", "message": "media path does not belong to this device" }
              },
              "cursor_conflict": {
                "summary": "补拉参数冲突",
                "value": { "ok": false, "code": "cursor_conflict", "message": "after_id and before_id cannot be used together" }
              }
            }
          }
        }
      }
    },
    "schemas": {
      "ErrorResponse": {
        "type": "object",
        "properties": {
          "ok": { "type": "boolean", "example": false },
          "code": { "type": "string", "example": "send_failed" },
          "message": { "type": "string", "description": "错误说明" }
        }
      },
      "PublicSendResponse": {
        "type": "object",
        "properties": {
          "ok": { "type": "boolean", "example": true },
          "protocol_version": { "type": "string", "example": "v1" },
          "kind": { "type": "string" },
          "outbox_id": { "type": "integer", "format": "int64", "description": "兼容字段：多目标发送时指向第一个 outbox。" },
          "outbox_ids": { "type": "array", "items": { "type": "integer", "format": "int64" }, "description": "本次请求创建的全部 outbox ID，顺序与 wx_ids 一致。" },
          "chat_record_id": { "type": "integer", "format": "int64", "description": "真实微信本地消息 ID。入队响应通常为空；手机模块 ACK 后请以 outbox.chat_record_id 为准。" },
          "status_url": { "type": "string", "example": "/api/v1/outbox/123" },
          "outbox": { "$ref": "#/components/schemas/PublicOutboxEnvelope", "description": "兼容字段：多目标发送时指向第一个 outbox。" },
          "outboxes": { "type": "array", "items": { "$ref": "#/components/schemas/PublicOutboxEnvelope" }, "description": "本次请求创建的全部 outbox，顺序与 wx_ids 一致。" },
          "warnings": { "type": "array", "items": { "type": "string" } }
        }
      },
      "PublicOutboxEnvelope": {
        "type": "object",
        "description": "外部可见的发送任务状态，不暴露 payload_json、api_key 或内部执行细节。",
        "properties": {
          "id": { "type": "integer", "format": "int64" },
          "device": { "type": "string" },
          "owner_wxid": { "type": "string" },
          "target_wxid": { "type": "string", "description": "发送目标 wxid 或群 room id。" },
          "kind": { "type": "string" },
          "text": { "type": "string" },
          "status": { "type": "string", "enum": ["pending", "leased", "sent", "failed"] },
          "status_url": { "type": "string" },
          "chat_record_id": { "type": "integer", "format": "int64" },
          "media": { "type": "array", "items": { "$ref": "#/components/schemas/PublicMessageMedia" } },
          "attempt_count": { "type": "integer" },
          "last_error": { "type": "string" },
          "created_at": { "type": "string" },
          "updated_at": { "type": "string" }
        }
      },
      "SendActionResponse": {
        "type": "object",
        "properties": {
          "ok": { "type": "boolean", "example": true },
          "chat_record_id": { "type": "integer", "format": "int64" },
          "outbox_id": { "type": "integer", "format": "int64" }
        }
      },
      "SendTarget": {
        "type": "object",
        "required": ["wx_ids"],
        "properties": {
          "device": { "type": "string", "example": "phone-a", "description": "服务端设备名。API Key 已绑定设备时可省略。" },
          "owner_wxid": { "type": "string", "description": "该设备当前登录的微信 wxid。API Key 已绑定并完成模块注册时可省略，服务端会用当前 owner_wxid 防切号误发。" },
          "wx_ids": {
            "type": "array",
            "items": { "type": "string" },
            "description": "目标好友 wxid 或群 room id。不要传昵称、备注或群名。"
          }
        }
      },
      "TextMessageRequest": {
        "allOf": [
          { "$ref": "#/components/schemas/SendTarget" },
          {
            "type": "object",
            "required": ["text"],
            "properties": {
              "text": { "type": "string", "description": "要发送的文本内容。" }
            }
          }
        ]
      },
      "MediaMessageRequest": {
        "allOf": [
          { "$ref": "#/components/schemas/SendTarget" },
          {
            "type": "object",
            "properties": {
              "text": { "type": "string", "description": "可选展示文本；为空时服务端按类型生成默认文本。" },
              "media_url": { "type": "string", "description": "已有 /api/media/... 媒体地址。" },
              "media_base64": { "type": "string", "description": "直接上传媒体内容；不要写入日志或示例输出。" },
              "media_mime": { "type": "string", "description": "媒体 MIME，例如 image/png、video/mp4、text/plain。" },
              "media_name": { "type": "string", "description": "媒体文件名，文件发送时建议提供。" }
            }
          }
        ]
      },
      "EmojiMessageRequest": {
        "allOf": [
          { "$ref": "#/components/schemas/SendTarget" },
          {
            "type": "object",
            "oneOf": [
              { "required": ["emoji_md5"] },
              { "required": ["source_chat_record_id"] }
            ],
            "properties": {
              "emoji_md5": { "type": "string", "description": "表情 MD5。" },
              "emoji_product_id": { "type": "string" },
              "source_chat_record_id": { "type": "integer", "format": "int64", "description": "已有表情消息记录 ID，用于原样转发。" }
            }
          }
        ]
      },
      "LocationMessageRequest": {
        "allOf": [
          { "$ref": "#/components/schemas/SendTarget" },
          {
            "type": "object",
            "required": ["location_latitude", "location_longitude"],
            "properties": {
              "location_latitude": { "type": "number", "format": "double" },
              "location_longitude": { "type": "number", "format": "double" },
              "location_scale": { "type": "integer", "default": 16 },
              "location_label": { "type": "string" },
              "location_poiname": { "type": "string" },
              "location_info_url": { "type": "string" },
              "location_poi_id": { "type": "string" }
            }
          }
        ]
      },
      "QuoteMessageRequest": {
        "allOf": [
          { "$ref": "#/components/schemas/SendTarget" },
          {
            "type": "object",
            "required": ["text", "quote_msg_id"],
            "properties": {
              "text": { "type": "string" },
              "quote_msg_id": { "type": "integer", "format": "int64" },
              "quote_chat_record_id": { "type": "integer", "format": "int64" },
              "quote_talker": { "type": "string" },
              "quote_sender_wxid": { "type": "string" }
            }
          }
        ]
      },
      "LinkMessageRequest": {
        "allOf": [
          { "$ref": "#/components/schemas/SendTarget" },
          {
            "type": "object",
            "oneOf": [
              { "required": ["source_chat_record_id"] },
              { "required": ["appmsg_title", "appmsg_url"] }
            ],
            "properties": {
              "text": { "type": "string" },
              "appmsg_title": { "type": "string" },
              "appmsg_description": { "type": "string" },
              "appmsg_url": { "type": "string" },
              "appmsg_app_name": { "type": "string" },
              "appmsg_thumb_url": { "type": "string" },
              "source_chat_record_id": { "type": "integer", "format": "int64", "description": "已有链接消息记录 ID，用于原样转发。" }
            }
          }
        ]
      },
      "MiniProgramMessageRequest": {
        "allOf": [
          { "$ref": "#/components/schemas/SendTarget" },
          {
            "type": "object",
            "oneOf": [
              { "required": ["source_chat_record_id"] },
              { "required": ["appmsg_title", "mini_program_username", "mini_program_page_path"] }
            ],
            "properties": {
              "text": { "type": "string" },
              "appmsg_title": { "type": "string" },
              "appmsg_description": { "type": "string" },
              "mini_program_username": { "type": "string" },
              "mini_program_page_path": { "type": "string" },
              "mini_program_appid": { "type": "string" },
              "mini_program_icon_url": { "type": "string" },
              "mini_program_version": { "type": "integer" },
              "mini_program_type": { "type": "integer" },
              "source_chat_record_id": { "type": "integer", "format": "int64", "description": "已有小程序消息记录 ID，用于原样转发。" }
            }
          }
        ]
      },
      "ChatHistoryMessageRequest": {
        "allOf": [
          { "$ref": "#/components/schemas/SendTarget" },
          {
            "type": "object",
            "oneOf": [
              { "required": ["recorditem_xml"] },
              { "required": ["source_chat_record_ids"] },
              { "required": ["forward_original", "source_chat_record_id"] }
            ],
            "properties": {
              "text": { "type": "string" },
              "record_title": { "type": "string" },
              "record_description": { "type": "string" },
              "recorditem_xml": { "type": "string" },
              "forward_original": { "type": "boolean" },
              "source_chat_record_id": { "type": "integer", "format": "int64" },
              "source_chat_record_ids": {
                "type": "array",
                "items": { "type": "integer", "format": "int64" }
              }
            }
          }
        ]
      },
      "RevokeMessageRequest": {
        "allOf": [
          { "$ref": "#/components/schemas/SendTarget" },
          {
            "type": "object",
            "required": ["target_chat_record_id"],
            "properties": {
              "target_chat_record_id": { "type": "integer", "format": "int64", "description": "要撤回的目标消息本地 chat_record_id，来自发送 ACK。" },
              "target_msg_svr_id": { "type": "integer", "format": "int64", "description": "可选的服务端消息 ID，用于手机端查找兜底。" },
              "revoke_ticket": { "type": "string", "description": "微信内部撤回 ticket；通常不需要外部填写。" }
            }
          }
        ]
      },
      "SendTextRequest": {
        "type": "object",
        "required": ["device", "owner_wxid", "wx_ids", "text"],
        "properties": {
          "device": { "type": "string", "example": "phone-a" },
          "owner_wxid": { "type": "string", "description": "该设备当前登录的微信 wxid，用来防止切号后误发。" },
          "wx_ids": {
            "type": "array",
            "items": { "type": "string" },
            "description": "目标好友 wxid 或群 room id。不要传昵称、备注或群名。"
          },
          "text": { "type": "string" }
        }
      },
      "SendActionRequest": {
        "type": "object",
        "required": ["device", "owner_wxid", "wx_ids", "kind"],
        "properties": {
          "device": { "type": "string", "example": "phone-a" },
          "owner_wxid": { "type": "string", "description": "该设备当前登录的微信 wxid，用来防止切号后误发。" },
          "wx_ids": {
            "type": "array",
            "items": { "type": "string" },
            "description": "目标好友 wxid 或群 room id。不要传显示名。"
          },
          "kind": {
            "type": "string",
            "enum": ["text", "image", "video", "voice", "file", "emoji", "location", "quote", "link", "mini_program", "chat_history", "revoke"]
          },
          "text": { "type": "string" },
          "media_kind": { "type": "string" },
          "media_mime": { "type": "string" },
          "media_name": { "type": "string" },
          "media_url": { "type": "string", "description": "已有的 /api/media/... 媒体地址。" },
          "media_size": { "type": "integer", "format": "int64" },
          "media_base64": { "type": "string", "description": "用于上传媒体内容；不要写入日志、文档或响应样例。" },
          "quote_msg_id": { "type": "integer", "format": "int64" },
          "quote_chat_record_id": { "type": "integer", "format": "int64" },
          "quote_talker": { "type": "string" },
          "quote_sender_wxid": { "type": "string" },
          "appmsg_title": { "type": "string" },
          "appmsg_description": { "type": "string" },
          "appmsg_url": { "type": "string" },
          "appmsg_app_name": { "type": "string" },
          "appmsg_thumb_url": { "type": "string" },
          "mini_program_username": { "type": "string" },
          "mini_program_page_path": { "type": "string" },
          "mini_program_appid": { "type": "string" },
          "mini_program_icon_url": { "type": "string" },
          "mini_program_version": { "type": "integer" },
          "mini_program_type": { "type": "integer" },
          "emoji_md5": { "type": "string" },
          "emoji_product_id": { "type": "string" },
          "record_title": { "type": "string" },
          "record_description": { "type": "string" },
          "recorditem_xml": { "type": "string" },
          "forward_original": { "type": "boolean" },
          "source_chat_record_id": { "type": "integer", "format": "int64" },
          "source_chat_record_ids": {
            "type": "array",
            "items": { "type": "integer", "format": "int64" }
          },
          "target_chat_record_id": { "type": "integer", "format": "int64" },
          "target_msg_svr_id": { "type": "integer", "format": "int64" },
          "revoke_ticket": { "type": "string" },
          "location_latitude": { "type": "number", "format": "double" },
          "location_longitude": { "type": "number", "format": "double" },
          "location_scale": { "type": "integer" },
          "location_label": { "type": "string" },
          "location_poiname": { "type": "string" },
          "location_info_url": { "type": "string" },
          "location_poi_id": { "type": "string" },
          "location_from_poi_list": { "type": "boolean" },
          "location_poi_category_tips": { "type": "string" }
        }
      },
      "CapabilitiesResponse": {
        "type": "object",
        "properties": {
          "ok": { "type": "boolean" },
          "protocol": { "type": "string", "example": "wechat-observatory" },
          "protocol_version": { "type": "string", "example": "v1" },
	          "envelope": {
	            "type": "object",
	            "description": "MessageEnvelope v1 contract. Field names match the public /api/v1/messages response; nested fields use paths like media[].url, appmsg.title, and location.latitude.",
	            "properties": {
	              "name": { "type": "string", "example": "MessageEnvelope v1" },
	              "fields": {
                "type": "array",
                "items": {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "type": { "type": "string" },
                    "required": { "type": "boolean" },
                    "description": { "type": "string" }
                  }
                }
              }
            }
          },
          "capabilities": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "kind": { "type": "string" },
                "subtype": { "type": "string" },
                "send_kind": { "type": "string" },
                "title": { "type": "string" },
                "inbound_status": { "type": "string" },
                "outbound_status": { "type": "string" },
                "verification": { "type": "string" },
                "message_types": { "type": "array", "items": { "type": "integer", "format": "int32" } },
                "appmsg_types": { "type": "array", "items": { "type": "integer", "format": "int32" } },
                "send_endpoint": { "type": "string" },
                "required_fields": { "type": "array", "items": { "type": "string" } },
                "optional_fields": { "type": "array", "items": { "type": "string" } },
                "unsupported": { "type": "array", "items": { "type": "string" } },
                "notes": { "type": "array", "items": { "type": "string" } }
              }
            }
          },
          "transports": { "type": "array", "items": { "type": "object" } },
          "limits": { "type": "object" }
        }
      },
      "PublicMessageEnvelope": {
        "type": "object",
        "description": "MessageEnvelope v1。外部接口和 WebSocket 使用的稳定消息协议对象，不包含 api_key、raw_xml、media_base64 或 raw_provider。",
        "properties": {
          "id": { "type": "string" },
          "event_id": { "type": "integer", "format": "int64" },
          "chat_record_id": { "type": "integer", "format": "int64" },
          "device": { "type": "string" },
          "owner_wxid": { "type": "string" },
          "direction": { "type": "string", "enum": ["recv", "sent"] },
          "kind": { "type": "string" },
          "subtype": { "type": "string" },
          "message_type": { "type": "integer", "format": "int32" },
          "appmsg_type": { "type": "integer", "format": "int32" },
          "chat_id": { "type": "string", "description": "稳定会话 ID；发送目标也应使用 wxid 或 room id。" },
          "chat_kind": { "type": "string", "enum": ["direct", "room", "unknown"] },
          "from_wxid": { "type": "string" },
          "to_wxid": { "type": "string" },
          "room_id": { "type": "string" },
          "sender_wxid": { "type": "string" },
          "text": { "type": "string" },
          "media": { "type": "array", "items": { "$ref": "#/components/schemas/PublicMessageMedia" } },
          "appmsg": { "$ref": "#/components/schemas/PublicAppMsgEnvelope" },
          "location": { "$ref": "#/components/schemas/PublicLocationEnvelope" },
          "unsupported": { "type": "array", "items": { "type": "string" } },
          "evidence": { "type": "array", "items": { "type": "string" } },
          "create_time": { "type": "integer", "format": "int64" },
          "created_at": { "type": "string" },
          "chat_display_name": { "type": "string", "description": "只用于页面展示，不能作为发送目标。" }
        }
      },
      "PublicMessageMedia": {
        "type": "object",
        "properties": {
          "kind": { "type": "string" },
          "mime": { "type": "string" },
          "name": { "type": "string" },
          "url": { "type": "string" },
          "size": { "type": "integer", "format": "int64" },
          "opaque": { "type": "boolean", "description": "true 表示该附件是微信本地 opaque 原始文件，适配器不应假设浏览器可直接预览；表情应优先使用 appmsg.url/appmsg.title 等结构字段。" }
        }
      },
      "PublicAppMsgEnvelope": {
        "type": "object",
        "properties": {
          "type": { "type": "integer", "format": "int32" },
          "subtype": { "type": "string" },
          "title": { "type": "string" },
          "description": { "type": "string" },
          "url": { "type": "string" },
          "file_name": { "type": "string" },
          "app_name": { "type": "string" }
        }
      },
      "PublicLocationEnvelope": {
        "type": "object",
        "properties": {
          "latitude": { "type": "number", "format": "double" },
          "longitude": { "type": "number", "format": "double" },
          "scale": { "type": "integer" },
          "label": { "type": "string" },
          "poiname": { "type": "string" },
          "info_url": { "type": "string" },
          "poi_id": { "type": "string" },
          "from_poi_list": { "type": "boolean" },
          "poi_category_tips": { "type": "string" }
        }
      },
      "MessageEvent": {
        "type": "object",
        "properties": {
          "api_key": { "type": "string" },
          "id": { "type": "string" },
          "event_id": { "type": "integer", "format": "int64" },
          "chat_record_id": { "type": "integer", "format": "int64" },
          "device": { "type": "string" },
          "owner_wxid": { "type": "string" },
          "kind": { "type": "string" },
          "from": { "type": "string" },
          "to": { "type": "string" },
          "room_id": { "type": "string" },
          "sender": { "type": "string" },
          "text": { "type": "string" },
          "message_type": { "type": "integer", "format": "int32" },
          "raw_xml": { "type": "string" },
          "appmsg_type": { "type": "integer", "format": "int32" },
          "appmsg_subtype": { "type": "string" },
          "appmsg_title": { "type": "string" },
          "appmsg_description": { "type": "string" },
          "appmsg_url": { "type": "string" },
          "appmsg_file_name": { "type": "string" },
          "appmsg_app_name": { "type": "string" },
          "unsupported": { "type": "array", "items": { "type": "string" } },
          "evidence": { "type": "array", "items": { "type": "string" } },
          "location_latitude": { "type": "number", "format": "double" },
          "location_longitude": { "type": "number", "format": "double" },
          "location_scale": { "type": "integer" },
          "location_label": { "type": "string" },
          "location_poiname": { "type": "string" },
          "location_info_url": { "type": "string" },
          "location_poi_id": { "type": "string" },
          "location_from_poi_list": { "type": "boolean" },
          "location_poi_category_tips": { "type": "string" },
          "media_kind": { "type": "string" },
          "media_mime": { "type": "string" },
          "media_name": { "type": "string" },
          "media_url": { "type": "string" },
          "media_size": { "type": "integer", "format": "int64" },
          "create_time": { "type": "integer", "format": "int64" },
          "direction": { "type": "string", "enum": ["recv", "sent"] },
          "raw_provider": { "type": "string" },
          "chat_kind": { "type": "string", "enum": ["direct", "room", "unknown"] },
          "chat_id": { "type": "string" },
          "chat_display_name": { "type": "string", "description": "只用于页面展示的联系人显示名，不能作为发送目标。" }
        }
      },
      "ModuleContact": {
        "type": "object",
        "properties": {
          "id": { "type": "integer", "format": "int64" },
          "device": { "type": "string" },
          "owner_wxid": { "type": "string" },
          "wxid": { "type": "string" },
          "nickname": { "type": "string" },
          "remark": { "type": "string" },
          "alias": { "type": "string" },
          "type": { "type": "integer" },
          "verify_flag": { "type": "integer" },
          "chatroom": { "type": "boolean" },
          "deleted": { "type": "boolean" },
          "last_seen_at": { "type": "string" },
          "updated_at": { "type": "string" }
        }
      },
      "ModuleStatus": {
        "type": "object",
        "properties": {
          "device": { "type": "string" },
          "device_wxid": { "type": "string" },
          "device_nickname": { "type": "string" },
          "enabled": { "type": "boolean" },
          "runtime_status": { "type": "string" },
          "pending_outbox": { "type": "integer", "format": "int64" },
          "leased_outbox": { "type": "integer", "format": "int64" },
          "sent_outbox": { "type": "integer", "format": "int64" },
          "failed_outbox": { "type": "integer", "format": "int64" },
          "last_outbox_error": { "type": "string" },
          "last_register_at": { "type": "string" },
          "last_poll_at": { "type": "string" },
          "last_ack_at": { "type": "string" }
        }
      },
      "ModuleRegistrationRequest": {
        "type": "object",
        "required": ["api_key", "wxid"],
        "properties": {
          "api_key": { "type": "string" },
          "device": { "type": "string" },
          "wxid": { "type": "string" },
          "nickname": { "type": "string" }
        }
      },
      "ModuleRegistrationResult": {
        "type": "object",
        "properties": {
          "device": { "$ref": "#/components/schemas/ModuleDevice" }
        }
      },
      "ModuleDevice": {
        "type": "object",
        "properties": {
          "name": { "type": "string" },
          "wxid": { "type": "string" },
          "nickname": { "type": "string" }
        }
      },
      "ModulePollRequest": {
        "type": "object",
        "required": ["api_key", "device"],
        "properties": {
          "api_key": { "type": "string" },
          "device": { "type": "string" },
          "wxid": { "type": "string" },
          "limit": { "type": "integer", "default": 1 }
        }
      },
      "ModuleAckRequest": {
        "type": "object",
        "required": ["api_key", "device"],
        "properties": {
          "api_key": { "type": "string" },
          "device": { "type": "string" },
          "wxid": { "type": "string" },
          "ids": {
            "type": "array",
            "items": { "type": "integer", "format": "int64" }
          },
          "items": {
            "type": "array",
            "items": { "$ref": "#/components/schemas/ModuleAckItem" }
          },
          "error": { "type": "string" }
        }
      },
      "ModuleAckItem": {
        "type": "object",
        "required": ["id", "status"],
        "properties": {
          "id": { "type": "integer", "format": "int64" },
          "status": { "type": "string", "enum": ["sent", "failed"] },
          "error": { "type": "string" },
          "chat_record_id": { "type": "integer", "format": "int64" }
        }
      },
      "ModuleOutboxItem": {
        "type": "object",
        "properties": {
          "id": { "type": "integer", "format": "int64" },
          "device": { "type": "string" },
          "owner_wxid": { "type": "string" },
          "wxid": { "type": "string", "description": "目标好友 wxid 或群 room id。" },
          "kind": { "type": "string" },
          "text": { "type": "string" },
          "payload_json": { "type": "object", "additionalProperties": true },
          "media_kind": { "type": "string" },
          "media_mime": { "type": "string" },
          "media_name": { "type": "string" },
          "media_url": { "type": "string" },
          "media_size": { "type": "integer", "format": "int64" },
          "chat_record_id": { "type": "integer", "format": "int64" },
          "status": { "type": "string" },
          "attempt_count": { "type": "integer" },
          "last_error": { "type": "string" },
          "created_at": { "type": "string" },
          "updated_at": { "type": "string" }
        }
      }
    }
  }
}
`

var publicDocFiles = map[string]string{
	"api.md":                                   "docs/api.md",
	"adapter-quickstart-v1.md":                 "docs/adapter-quickstart-v1.md",
	"capability-evidence-v1.md":                "docs/capability-evidence-v1.md",
	"protocol-capability-review-2026-07-03.md": "docs/protocol-capability-review-2026-07-03.md",
	"protocol-stability-review-v1.md":          "docs/protocol-stability-review-v1.md",
	"public-api-errors-v1.md":                  "docs/public-api-errors-v1.md",
	"public-api-message-samples-v1.md":         "docs/public-api-message-samples-v1.md",
	"public-api-python-client-v1.md":           "docs/public-api-python-client-v1.md",
}

func (s *HTTPServer) openAPIDocs(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path != "/docs" && r.URL.Path != "/docs/" {
		s.openAPIDocFile(w, r)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(openAPIDocsHTML))
}

func (s *HTTPServer) openAPIDocFile(w http.ResponseWriter, r *http.Request) {
	name := strings.TrimPrefix(r.URL.Path, "/docs/")
	docPath, ok := publicDocFiles[name]
	if !ok || name == "" || strings.Contains(name, "/") || strings.Contains(name, `\`) {
		http.NotFound(w, r)
		return
	}
	data, err := readPublicDocFile(docPath)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "text/markdown; charset=utf-8")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(data)
}

func readPublicDocFile(docPath string) ([]byte, error) {
	candidates := []string{
		docPath,
		filepath.Join("../..", docPath),
		filepath.Join("/usr/share/wechat-observatory", docPath),
	}
	var lastErr error
	for _, candidate := range candidates {
		data, err := os.ReadFile(candidate)
		if err == nil {
			return data, nil
		}
		lastErr = err
	}
	return nil, lastErr
}

func (s *HTTPServer) openAPIJSON(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(openAPIJSONDocument))
}
