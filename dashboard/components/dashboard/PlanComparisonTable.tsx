type Plan = 'FREE' | 'STARTER' | 'PRO' | 'TEAM';

type Props = {
  currentPlan: Plan;
  onUpgrade: (plan: Plan) => void;
  upgrading: Plan | null;
};

const PLANS: { plan: Plan; label: string; products: string; recs: string; price: string }[] = [
  { plan: 'FREE',    label: 'Free',    products: '2',         recs: '100/mo',      price: '€0' },
  { plan: 'STARTER', label: 'Starter', products: '10',        recs: '5,000/mo',    price: '€29/mo' },
  { plan: 'PRO',     label: 'Pro',     products: '50',        recs: '25,000/mo',   price: '€79/mo' },
  { plan: 'TEAM',    label: 'Team',    products: 'Unlimited', recs: 'Unlimited',   price: '€149/mo' },
];

const ORDER: Record<Plan, number> = { FREE: 0, STARTER: 1, PRO: 2, TEAM: 3 };

export function PlanComparisonTable({ currentPlan, onUpgrade, upgrading }: Readonly<Props>) {
  return (
    <div className="overflow-x-auto rounded-lg border border-border">
      <table className="w-full min-w-[480px] text-sm">
        <thead className="bg-muted/50 text-left text-muted-foreground">
          <tr>
            <th className="px-4 py-2 font-medium">Plan</th>
            <th className="px-4 py-2 font-medium">Products</th>
            <th className="px-4 py-2 font-medium">Recommendations</th>
            <th className="px-4 py-2 font-medium">Price</th>
            <th className="px-4 py-2 font-medium" />
          </tr>
        </thead>
        <tbody>
          {PLANS.map(({ plan, label, products, recs, price }) => {
            const isCurrent = plan === currentPlan;
            const isUpgrade = ORDER[plan] > ORDER[currentPlan];
            return (
              <tr key={plan} className={`border-t border-border/70 ${isCurrent ? 'bg-muted/30' : ''}`}>
                <td className="px-4 py-2 font-medium">
                  {label}
                  {isCurrent && (
                    <span className="ml-2 inline-flex rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">
                      Current
                    </span>
                  )}
                </td>
                <td className="px-4 py-2 tabular-nums">{products}</td>
                <td className="px-4 py-2 tabular-nums">{recs}</td>
                <td className="px-4 py-2 font-medium">{price}</td>
                <td className="px-4 py-2">
                  {isUpgrade && (
                    <button
                      type="button"
                      disabled={upgrading === plan}
                      onClick={() => onUpgrade(plan)}
                      className="rounded-md bg-foreground px-3 py-1.5 text-xs font-medium text-background hover:opacity-80 disabled:opacity-50"
                    >
                      {upgrading === plan ? 'Redirecting...' : `Upgrade to ${label}`}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
