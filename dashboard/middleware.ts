import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

const storeProtectedPrefixes = ['/dashboard', '/products', '/settings'];
const adminProtectedPrefixes = ['/admin'];
const authRoutes = ['/login', '/register'];
const COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24;

function withProductionCookie(response: NextResponse, token: string | undefined): NextResponse {
  if (process.env.NODE_ENV === 'production' && token) {
    response.cookies.set('fitvision_token', token, {
      secure: true,
      sameSite: 'strict',
      path: '/',
      maxAge: COOKIE_MAX_AGE_SECONDS
    });
  }
  return response;
}

function decodeRoleFromToken(token: string | undefined): string | null {
  if (!token) {
    return null;
  }

  try {
    const tokenValue = decodeURIComponent(token);
    const parts = tokenValue.split('.');
    if (parts.length < 2) {
      return null;
    }

    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const json = atob(padded);
    const payload = JSON.parse(json) as { role?: unknown };

    return typeof payload.role === 'string' ? payload.role : null;
  } catch {
    return null;
  }
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const token = request.cookies.get('fitvision_token')?.value;
  const role = decodeRoleFromToken(token);

  const isStoreProtected = storeProtectedPrefixes.some((route) => pathname.startsWith(route));
  const isAdminProtected = adminProtectedPrefixes.some((route) => pathname.startsWith(route));
  const isAuthRoute = authRoutes.some((route) => pathname === route || pathname.startsWith(`${route}/`));

  if ((isStoreProtected || isAdminProtected) && !token) {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  if (isStoreProtected && role === 'ADMIN') {
    return withProductionCookie(
      NextResponse.redirect(new URL('/admin/dashboard', request.url)),
      token
    );
  }

  if (isAdminProtected && role !== 'ADMIN') {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  if (isAuthRoute && token) {
    const target = role === 'ADMIN' ? '/admin/dashboard' : '/dashboard';
    return withProductionCookie(NextResponse.redirect(new URL(target, request.url)), token);
  }

  return withProductionCookie(NextResponse.next(), token);
}

export const config = {
  matcher: ['/dashboard/:path*', '/products/:path*', '/settings/:path*', '/admin/:path*', '/login', '/register']
};
