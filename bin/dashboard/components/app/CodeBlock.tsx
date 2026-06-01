import { CopyButton } from '@/components/app/CopyButton';

type CodeBlockProps = {
  code: string;
};

export function CodeBlock({ code }: Readonly<CodeBlockProps>) {
  return (
    <div className="rounded-lg border border-border bg-muted/30">
      <div className="flex items-center justify-between border-b border-border px-3 py-2">
        <p className="text-xs uppercase tracking-wide text-muted-foreground">Integration Snippet</p>
        <CopyButton text={code} label="Copy snippet" />
      </div>
      <pre className="overflow-x-auto p-4 text-sm leading-relaxed text-foreground">
        <code>{code}</code>
      </pre>
    </div>
  );
}
