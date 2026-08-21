import { Skeleton } from "@/components/ui/skeleton";

export function JobDetailsSkeleton() {
    return (
        <main className="grid h-full min-h-0 md:grid-cols-[minmax(0,1fr)_22rem]">
            <Skeleton className="rounded-none" />
            <div className="hidden space-y-4 border-l p-4 md:block">
                <Skeleton className="h-12" />
                <Skeleton className="h-32" />
                <Skeleton className="h-32" />
                <Skeleton className="h-32" />
            </div>
        </main>
    );
}
