import { Link } from "@tanstack/react-router";
import { ArrowRightIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { m } from "@/paraglide/messages";

export function NewJobButton({ className }: { className?: string }) {
    return (
        <Button
            className={cn("h-10 px-4", className)}
            nativeButton={false}
            render={<Link to="/" />}
        >
            {m.super_crazy_anaconda_wave()}
            <ArrowRightIcon data-icon="inline-end" />
        </Button>
    );
}
