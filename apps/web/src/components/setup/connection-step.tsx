import { Globe2Icon, NetworkIcon } from "lucide-react";
import {
    Card,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { m } from "@/paraglide/messages";

export function ConnectionStep() {
    const options = [
        {
            icon: Globe2Icon,
            title: m.setup_connection_direct(),
            description: m.setup_connection_direct_description(),
        },
        {
            icon: NetworkIcon,
            title: m.setup_connection_tunnel(),
            description: m.setup_connection_tunnel_description(),
        },
    ];

    return (
        <div className="mx-auto grid max-w-3xl gap-4 md:grid-cols-2">
            {options.map((option) => (
                <Card key={option.title} className="relative min-h-52">
                    <CardHeader>
                        <span className="mb-5 grid size-12 place-items-center rounded-xl bg-muted">
                            <option.icon className="size-6" />
                        </span>
                        <CardTitle>{option.title}</CardTitle>
                        <CardDescription>{option.description}</CardDescription>
                    </CardHeader>
                    <span className="absolute top-4 right-4 rounded-full bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground">
                        {m.setup_coming_soon()}
                    </span>
                </Card>
            ))}
        </div>
    );
}
