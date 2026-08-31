import { ArrowRightIcon } from "lucide-react";
import { AtlasLogo } from "@/components/atlas-logo";
import { Button } from "@/components/ui/button";
import { m } from "@/paraglide/messages";

export function GreetingStep({ onStart }: { onStart: () => void }) {
    return (
        <section className="relative z-10 flex flex-1 flex-col items-center justify-center px-6 py-16 text-center">
            <div className="-mt-10 flex flex-col items-center">
                <p className="text-xl font-medium tracking-tight text-muted-foreground sm:text-2xl">
                    {m.setup_welcome_to()}
                </p>
                <div className="mt-5 flex items-center gap-4 sm:gap-6">
                    <AtlasLogo
                        className="size-24 sm:size-32"
                        globeClassName="animate-logo-orbit"
                    />
                    <span className="font-heading text-7xl font-semibold tracking-[-0.07em] sm:text-9xl">
                        Atlas
                    </span>
                </div>
                <p className="mt-3 text-sm font-medium tracking-[0.22em] text-muted-foreground uppercase sm:text-base">
                    {m.setup_product_subtitle()}
                </p>
            </div>
            <div className="absolute inset-x-6 bottom-20 flex flex-col items-center gap-5">
                <p className="max-w-md text-sm text-muted-foreground sm:text-base">
                    {m.setup_welcome_hint()}
                </p>
                <Button
                    type="button"
                    size="lg"
                    className="h-14 min-w-64 rounded-xl px-8 text-base shadow-lg shadow-black/10"
                    onClick={onStart}
                >
                    {m.setup_get_started()}
                    <ArrowRightIcon data-icon="inline-end" />
                </Button>
            </div>
        </section>
    );
}
