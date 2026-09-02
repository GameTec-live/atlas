import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

export function SystemSummary({
    icon: Icon,
    title,
    description,
    children,
}: {
    icon: LucideIcon;
    title: string;
    description: string;
    children: ReactNode;
}) {
    return (
        <section className="flex flex-row gap-2 rounded-xl p-4 justify-between flex-wrap">
            <div className="flex items-center gap-2">
                <Icon className="size-4 text-muted-foreground" />
                <div>
                    <h3 className="font-medium">{title}</h3>
                    <p className="text-sm text-muted-foreground">
                        {description}
                    </p>
                </div>
            </div>
            {children}
        </section>
    );
}
