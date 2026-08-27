import type { ReactNode } from "react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

function CardSkeleton({
    className,
    children,
}: {
    className?: string;
    children: ReactNode;
}) {
    return (
        <Card className={className}>
            <CardHeader className="gap-2">
                <Skeleton className="h-5 w-36" />
                <Skeleton className="h-4 w-72 max-w-full" />
            </CardHeader>
            <CardContent>{children}</CardContent>
        </Card>
    );
}

export function SettingsPageSkeleton() {
    return (
        <main className="mx-auto w-full max-w-7xl p-4 sm:p-6 lg:p-8">
            <div className="mb-6 space-y-2">
                <Skeleton className="h-3 w-24" />
                <Skeleton className="h-8 w-36" />
                <Skeleton className="h-4 w-96 max-w-full" />
            </div>
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
                <CardSkeleton className="lg:col-span-12">
                    <div className="overflow-hidden rounded-lg border">
                        {["header", "one", "two", "three"].map((row, index) => (
                            <Skeleton
                                key={row}
                                className={cn(
                                    "h-12 rounded-none border-b last:border-b-0",
                                    index === 0 && "h-10 bg-muted/80",
                                )}
                            />
                        ))}
                    </div>
                </CardSkeleton>
                <CardSkeleton className="lg:col-span-12">
                    <div className="grid gap-6 md:grid-cols-[11rem_1fr]">
                        <Skeleton className="aspect-square rounded-xl" />
                        <div className="grid content-start gap-5 sm:grid-cols-2">
                            <Skeleton className="h-20" />
                            <Skeleton className="h-20" />
                            <Skeleton className="h-12 sm:col-span-2" />
                        </div>
                    </div>
                </CardSkeleton>
                <CardSkeleton className="lg:col-span-4">
                    <div className="grid gap-4">
                        <Skeleton className="h-12" />
                        <Skeleton className="h-12" />
                        <Skeleton className="h-12" />
                    </div>
                </CardSkeleton>
                <CardSkeleton className="lg:col-span-8">
                    <div className="grid gap-3">
                        <Skeleton className="h-12" />
                        <Skeleton className="h-20" />
                        <Skeleton className="h-20" />
                    </div>
                </CardSkeleton>
            </div>
        </main>
    );
}
