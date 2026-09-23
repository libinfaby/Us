// Firebase Cloud Messaging via the HTTP v1 API, authenticated with a service
// account's private key. Signs its own OAuth2 JWT using the Web Crypto API
// (RSASSA-PKCS1-v1_5/SHA-256) - same "no external crypto dependency"
// approach as lib/crypto.ts - then exchanges it for a short-lived Google
// access token.

type ServiceAccount = {
  client_email: string;
  private_key: string;
  project_id: string;
  token_uri?: string;
};

function base64UrlEncode(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function pemToPkcs8(pem: string): ArrayBuffer {
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s+/g, '');
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  return crypto.subtle.importKey('pkcs8', pemToPkcs8(pem), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, [
    'sign',
  ]);
}

async function signJwt(claims: Record<string, unknown>, privateKeyPem: string): Promise<string> {
  const encoder = new TextEncoder();
  const headerPart = base64UrlEncode(encoder.encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
  const claimsPart = base64UrlEncode(encoder.encode(JSON.stringify(claims)));
  const signingInput = `${headerPart}.${claimsPart}`;
  const key = await importPrivateKey(privateKeyPem);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, encoder.encode(signingInput));
  return `${signingInput}.${base64UrlEncode(new Uint8Array(signature))}`;
}

async function fetchAccessToken(sa: ServiceAccount): Promise<{ accessToken: string; expiresAt: number }> {
  const now = Math.floor(Date.now() / 1000);
  const tokenUri = sa.token_uri ?? 'https://oauth2.googleapis.com/token';
  const jwt = await signJwt(
    {
      iss: sa.client_email,
      scope: 'https://www.googleapis.com/auth/firebase.messaging',
      aud: tokenUri,
      iat: now,
      exp: now + 3600,
    },
    sa.private_key,
  );

  const res = await fetch(tokenUri, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  });
  if (!res.ok) throw new Error(`FCM token exchange failed: ${res.status} ${await res.text()}`);
  const json = (await res.json()) as { access_token: string; expires_in: number };
  return { accessToken: json.access_token, expiresAt: Date.now() + json.expires_in * 1000 };
}

// Module-level cache: a pure optimization that survives across requests when
// the Workers isolate is reused, and is safely refetched on a cold start.
let cached: { accessToken: string; expiresAt: number; projectId: string } | null = null;

async function getAccessToken(serviceAccountJson: string): Promise<{ accessToken: string; projectId: string }> {
  if (cached && Date.now() < cached.expiresAt - 60_000) return cached;
  const sa: ServiceAccount = JSON.parse(serviceAccountJson);
  const { accessToken, expiresAt } = await fetchAccessToken(sa);
  cached = { accessToken, expiresAt, projectId: sa.project_id };
  return cached;
}

export type FcmResult = { ok: true } | { ok: false; invalidToken: boolean; status: number };

export async function sendFcmMessage(
  serviceAccountJson: string,
  deviceToken: string,
  title: string,
  body: string,
  data: Record<string, string>,
): Promise<FcmResult> {
  const { accessToken, projectId } = await getAccessToken(serviceAccountJson);
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: { token: deviceToken, notification: { title, body }, data, android: { priority: 'high' } },
    }),
  });
  if (res.ok) return { ok: true };

  const errBody = (await res.json().catch(() => null)) as { error?: { status?: string } } | null;
  const status = errBody?.error?.status;
  const invalidToken = status === 'UNREGISTERED' || status === 'INVALID_ARGUMENT' || status === 'NOT_FOUND';
  return { ok: false, invalidToken, status: res.status };
}
