import { createFileRoute } from "@tanstack/react-router";
import { LiveDriversMap } from "@/components/live-drivers-map";
import { NewJobButton } from "@/components/new-job-button";

export const Route = createFileRoute("/_authenticated/_app/realtime")({
    component: RealtimeMap,
});

function RealtimeMap() {
    return (
        <div className="h-full">
            <LiveDriversMap />

            <NewJobButton className="absolute right-4 bottom-4 z-10 shadow-lg" />
        </div>
    );
}
