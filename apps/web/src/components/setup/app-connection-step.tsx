import { SmartphoneIcon, SparklesIcon } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { m } from "@/paraglide/messages";

export function AppConnectionStep() {
    return (
        <Card className="mx-auto max-w-2xl">
            <CardContent className="flex flex-col items-center py-12 text-center">
                <div className="relative mb-7 grid size-24 place-items-center rounded-3xl bg-primary text-primary-foreground shadow-xl shadow-black/10">
                    <SmartphoneIcon className="size-11" />
                    <span className="absolute -top-2 -right-2 grid size-8 place-items-center rounded-full border-4 border-card bg-background text-foreground">
                        <SparklesIcon className="size-4" />
                    </span>
                </div>
                <h2 className="text-xl font-semibold">
                    {m.setup_app_placeholder_title()}
                </h2>
                <p className="mt-2 max-w-md text-sm leading-relaxed text-muted-foreground">
                    {m.setup_app_placeholder_description()}
                </p>
                <span className="mt-6 rounded-full bg-muted px-3 py-1.5 text-xs font-medium text-muted-foreground">
                    {m.setup_coming_soon()}
                </span>
            </CardContent>
        </Card>
    );
}
