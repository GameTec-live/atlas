import { Skeleton } from "@/components/ui/skeleton";

export function JobsPageSkeleton() {
    return (
        <main className="flex h-full min-h-0">
            <section className="min-w-0 flex-1">
                <div className="flex h-16 items-center border-b px-6">
                    <Skeleton className="h-6 w-32" />
                </div>
                <div className="grid gap-3 p-6 xl:grid-cols-2">
                    {["one", "two", "three", "four"].map((key) => (
                        <Skeleton key={key} className="h-44" />
                    ))}
                </div>
            </section>
            <aside className="hidden w-96 border-l p-4 lg:block">
                <Skeleton className="mb-8 h-6 w-40" />
                <div className="space-y-3">
                    {["one", "two", "three"].map((key) => (
                        <Skeleton key={key} className="h-32" />
                    ))}
                </div>
            </aside>
        </main>
    );
}
