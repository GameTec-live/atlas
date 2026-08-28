import { m } from "@/paraglide/messages";

type NoteValueProps = {
    value: string;
};

export function NoteValue({ value }: NoteValueProps) {
    return (
        <div className="flex min-h-12 min-w-0 flex-1 items-start gap-2 px-3 py-3">
            <span className="shrink-0 text-sm text-muted-foreground">
                {m.jobs_note()}
            </span>
            <p className="min-w-0 whitespace-pre-wrap wrap-break-word text-sm font-medium sm:text-base">
                {value}
            </p>
        </div>
    );
}
