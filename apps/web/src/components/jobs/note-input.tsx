import { Textarea } from "@/components/ui/textarea";
import { m } from "@/paraglide/messages";

type NoteInputProps = {
    value: string;
    onChange: (value: string) => void;
};

export function NoteInput({ value, onChange }: NoteInputProps) {
    return (
        <div className="relative min-w-0 flex-1">
            <span className="pointer-events-none absolute top-3 left-3 z-10 text-sm text-muted-foreground">
                {m.jobs_note()}
            </span>
            <Textarea
                value={value}
                onChange={(event) => onChange(event.target.value)}
                aria-label={m.jobs_note()}
                rows={1}
                className="min-h-12 resize-none rounded-none border-0 bg-transparent py-3 pl-16 text-sm shadow-none focus-visible:ring-0 sm:text-base"
            />
        </div>
    );
}
