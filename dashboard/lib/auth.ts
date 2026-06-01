const TOKEN_KEY = 'fitvision_access_token';
const TOKEN_COOKIE = 'fitvision_token';
const COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24;

function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

function cookieSuffix(): string {
  if (process.env.NODE_ENV === 'production') {
    return 'Path=/; SameSite=Strict; Secure';
  }
  return 'Path=/; SameSite=Lax';
}

export function saveToken(token: string): void {
  if (!isBrowser()) {
    return;
  }

  window.localStorage.setItem(TOKEN_KEY, token);
  document.cookie = `${TOKEN_COOKIE}=${encodeURIComponent(token)}; Max-Age=${COOKIE_MAX_AGE_SECONDS}; ${cookieSuffix()}`;
}

export function getToken(): string | null {
  if (!isBrowser()) {
    return null;
  }
  return window.localStorage.getItem(TOKEN_KEY);
}

export function clearToken(): void {
  if (!isBrowser()) {
    return;
  }

  window.localStorage.removeItem(TOKEN_KEY);
  document.cookie = `${TOKEN_COOKIE}=; Max-Age=0; ${cookieSuffix()}`;
}

export function isAuthenticated(): boolean {
  return Boolean(getToken());
}
