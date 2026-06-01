const MAX_VALUE = 2_147_483_647; // Integer.MAX_VALUE — TEAM plan "unlimited"

type Props = {
  label: string;
  used: number;
  limit: number;
};

export function UsageBar({ label, used, limit }: Readonly<Props>) {
  const isUnlimited = limit >= MAX_VALUE;
  const pct = isUnlimited ? 0 : Math.min(100, Math.round((used / limit) * 100));
  const atLimit = !isUnlimited && used >= limit;

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between text-sm">
        <span className="text-muted-foreground">{label}</span>
        <span className={`font-medium tabular-nums ${atLimit ? 'text-rose-600' : ''}`}>
          {isUnlimited ? `${used.toLocaleString()} / ∞` : `${used.toLocaleString()} / ${limit.toLocaleString()}`}
        </span>
      </div>
      {!isUnlimited && (
        <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
          <div
            className={`h-full rounded-full transition-all ${atLimit ? 'bg-rose-500' : pct >= 80 ? 'bg-amber-500' : 'bg-emerald-500'}`}
            style={{ width: `${pct}%` }}
          />
        </div>
      )}
    </div>
  );
}
