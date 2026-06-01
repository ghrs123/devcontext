export type JwtPayload = {
  sub?: string;
  exp?: number;
  role?: string;
  [key: string]: unknown;
};

function decodeBase64Url(value: string): string {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);

  if (typeof atob === 'function') {
    return atob(padded);
  }

  return Buffer.from(padded, 'base64').toString('utf-8');
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length < 2) {
      return null;
    }

    const json = decodeBase64Url(parts[1]);
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

export function getRoleFromToken(token: string | null | undefined): string | null {
  if (!token) {
    return null;
  }
  const payload = decodeJwtPayload(token);
  return typeof payload?.role === 'string' ? payload.role : null;
}
