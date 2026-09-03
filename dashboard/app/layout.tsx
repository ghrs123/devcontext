import type { Metadata } from 'next';
import { Toaster } from '@/components/ui/toaster';
import './globals.css';

export const metadata: Metadata = {
  title: 'FitVision — Store Dashboard',
  description:
    'Manage products, size charts, and size-recommendation analytics for your store.',
  icons: {
    icon: 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22%3E%3Crect width=%2232%22 height=%2232%22 rx=%228%22 fill=%22%234f46e5%22/%3E%3Cpath d=%22M9 22V10h9M9 16h7%22 stroke=%22white%22 stroke-width=%222.4%22 stroke-linecap=%22round%22 fill=%22none%22/%3E%3Ccircle cx=%2222%22 cy=%2221%22 r=%223%22 stroke=%22white%22 stroke-width=%222.2%22 fill=%22none%22/%3E%3C/svg%3E'
  }
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"
          rel="stylesheet"
        />
      </head>
      <body className="min-h-screen bg-background font-sans text-foreground antialiased">
        {children}
        <Toaster />
      </body>
    </html>
  );
}
