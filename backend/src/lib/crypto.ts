// PIN hashing (PBKDF2) and session token signing (HMAC), both via the Web
// Crypto API that's built into Workers - no external crypto dependency needed.

const PBKDF2_ITERATIONS = 100_000;

function toHex(bytes: Uint8Array): string {
  return [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function fromHex(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return bytes;
}

function timingSafeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

async function pbkdf2(pin: string, salt: Uint8Array): Promise<ArrayBuffer> {
  const keyMaterial = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, [
    'deriveBits',
  ]);
  return crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    keyMaterial,
    256,
  );
}

/** Returns a self-describing string safe to store in the `pin_hash` column. */
export async function hashPin(pin: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const hash = await pbkdf2(pin, salt);
  return `pbkdf2$${PBKDF2_ITERATIONS}$${toHex(salt)}$${toHex(new Uint8Array(hash))}`;
}

export async function verifyPin(pin: string, stored: string): Promise<boolean> {
  const [scheme, iterationsStr, saltHex, hashHex] = stored.split('$');
  if (scheme !== 'pbkdf2' || Number(iterationsStr) !== PBKDF2_ITERATIONS) return false;
  const candidate = await pbkdf2(pin, fromHex(saltHex));
  return timingSafeEqual(new Uint8Array(candidate), fromHex(hashHex));
}

function base64UrlEncode(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64UrlDecode(str: string): Uint8Array {
  const padded = str.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(str.length / 4) * 4, '=');
  return new Uint8Array([...atob(padded)].map((c) => c.charCodeAt(0)));
}

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, [
    'sign',
    'verify',
  ]);
}

export type SessionPayload = { u: 'pingu' | 'codu'; iat: number };

/** A compact `<payload>.<signature>` token, both parts base64url. Long-lived - this is a 2-person app. */
export async function signSession(payload: SessionPayload, secret: string): Promise<string> {
  const payloadBytes = new TextEncoder().encode(JSON.stringify(payload));
  const payloadPart = base64UrlEncode(payloadBytes);
  const signature = await crypto.subtle.sign('HMAC', await hmacKey(secret), payloadBytes);
  return `${payloadPart}.${base64UrlEncode(new Uint8Array(signature))}`;
}

export async function verifySession(token: string, secret: string): Promise<SessionPayload | null> {
  const [payloadPart, signaturePart] = token.split('.');
  if (!payloadPart || !signaturePart) return null;
  const payloadBytes = base64UrlDecode(payloadPart);
  const valid = await crypto.subtle.verify('HMAC', await hmacKey(secret), base64UrlDecode(signaturePart), payloadBytes);
  if (!valid) return null;
  try {
    return JSON.parse(new TextDecoder().decode(payloadBytes)) as SessionPayload;
  } catch {
    return null;
  }
}
