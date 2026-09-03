import { cn } from '@/lib/utils';

type LogoMarkProps = {
  className?: string;
};

/** The FitVision glyph: a caliper measuring a form. */
export function LogoMark({ className }: LogoMarkProps) {
  return (
    <svg viewBox="0 0 32 32" fill="none" className={className} aria-hidden="true">
      <rect width="32" height="32" rx="9" fill="hsl(var(--primary))" />
      <path
        d="M9 23V9h10"
        stroke="hsl(var(--primary-foreground))"
        strokeWidth="2.4"
        strokeLinecap="round"
      />
      <path d="M9 16h7.5" stroke="hsl(var(--primary-foreground))" strokeWidth="2.4" strokeLinecap="round" />
      <circle
        cx="21.5"
        cy="21"
        r="3.4"
        stroke="hsl(var(--primary-foreground))"
        strokeWidth="2.2"
      />
    </svg>
  );
}

type WordmarkProps = {
  className?: string;
  markClassName?: string;
};

export function Wordmark({ className, markClassName }: WordmarkProps) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <LogoMark className={cn('h-7 w-7 shrink-0', markClassName)} />
      <span className="text-[0.975rem] font-semibold tracking-tight">
        Fit<span className="text-primary">Vision</span>
      </span>
    </span>
  );
}
