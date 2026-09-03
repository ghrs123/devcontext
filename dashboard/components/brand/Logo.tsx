import { cn } from '@/lib/utils';

type LogoMarkProps = {
  className?: string;
};

/** The FitVision mark: four interlocking tiles (matches the product favicon). */
export function LogoMark({ className }: LogoMarkProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
      <path
        d="M12 12H4.7273C3.221 12 2 10.779 2 9.2727V4.7273C2 3.221 3.221 2 4.7273 2H9.2727C10.779 2 12 3.221 12 4.7273V12Z"
        fill="#2E9EFF"
      />
      <path
        d="M20 2C21.1046 2 22 2.8954 22 4V7C22 8.1046 21.1046 9 20 9H17C15.8954 9 15 8.1046 15 7V4C15 2.8954 15.8954 2 17 2H20Z"
        fill="#0C79D8"
      />
      <path
        d="M7 15C8.1046 15 9 15.8954 9 17V20C9 21.1046 8.1046 22 7 22H4C2.8954 22 2 21.1046 2 20V17C2 15.8954 2.8954 15 4 15H7Z"
        fill="#0C79D8"
      />
      <path
        d="M22 19.2727C22 20.779 20.779 22 19.2727 22H14.7273C13.221 22 12 20.779 12 19.2727V12H19.2727C20.779 12 22 13.221 22 14.7273V19.2727Z"
        fill="#68C4FF"
      />
    </svg>
  );
}

type WordmarkProps = {
  className?: string;
  markClassName?: string;
  /** Use light text (for dark backgrounds). */
  inverted?: boolean;
};

export function Wordmark({ className, markClassName, inverted }: WordmarkProps) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <LogoMark className={cn('h-6 w-6 shrink-0', markClassName)} />
      <span
        className={cn(
          'text-[0.975rem] font-semibold tracking-tight',
          inverted ? 'text-white' : 'text-foreground'
        )}
      >
        FitVision
      </span>
    </span>
  );
}
