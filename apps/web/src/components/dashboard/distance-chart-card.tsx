import { useSuspenseQuery } from "@tanstack/react-query";
import { RouteIcon } from "lucide-react";
import { Suspense, useMemo } from "react";
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";
import { DashboardCardHeader } from "@/components/dashboard/dashboard-card-header";
import { Card, CardContent } from "@/components/ui/card";
import {
    type ChartConfig,
    ChartContainer,
    ChartTooltip,
    ChartTooltipContent,
} from "@/components/ui/chart";
import { Skeleton } from "@/components/ui/skeleton";
import { getDailyDistanceData } from "@/lib/logbook";
import { formatNumber } from "@/lib/number";
import { m } from "@/paraglide/messages";
import { getLocale } from "@/paraglide/runtime";
import { logbooksQueryOptions } from "@/queries/logbooks";

const cardClassName = "min-h-112 xl:col-span-8 xl:min-h-0";

const chartConfig = {
    kilometers: {
        label: m.dashboard_kilometers(),
    },
} satisfies ChartConfig;

export function DistanceChartCard() {
    return (
        <Suspense
            fallback={
                <Skeleton className={`h-112 xl:h-auto ${cardClassName}`} />
            }
        >
            <DistanceChartCardContent />
        </Suspense>
    );
}

function DistanceChartCardContent() {
    const { data: entries } = useSuspenseQuery(logbooksQueryOptions());
    const locale = getLocale();
    const data = useMemo(
        () => getDailyDistanceData(entries, locale),
        [entries, locale],
    );
    const total = data.reduce((sum, day) => sum + day.kilometers, 0);

    return (
        <Card className={cardClassName}>
            <DashboardCardHeader
                title={m.dashboard_distance_title()}
                to="/logbook"
                icon={<RouteIcon />}
            />
            <CardContent className="flex min-h-0 flex-1 flex-col">
                <div className="mb-3">
                    <p className="font-heading text-3xl font-semibold tracking-tight tabular-nums">
                        {formatNumber(total, locale)}{" "}
                        <span className="text-base font-normal text-muted-foreground">
                            km
                        </span>
                    </p>
                    <p className="text-xs text-muted-foreground">
                        {m.dashboard_distance_total()}
                    </p>
                </div>
                <ChartContainer
                    config={chartConfig}
                    className="min-h-64 flex-1 aspect-auto xl:min-h-0"
                >
                    <BarChart
                        accessibilityLayer
                        data={data}
                        margin={{ top: 8, right: 4, left: -16, bottom: 0 }}
                    >
                        <CartesianGrid vertical={false} />
                        <XAxis
                            dataKey="day"
                            tickLine={false}
                            axisLine={false}
                            tickMargin={10}
                        />
                        <YAxis
                            tickLine={false}
                            axisLine={false}
                            tickFormatter={(value) =>
                                formatNumber(Number(value), locale)
                            }
                        />
                        <ChartTooltip
                            content={
                                <ChartTooltipContent
                                    indicator="line"
                                    formatter={(value) => (
                                        <div className="flex flex-1 items-center justify-between gap-8">
                                            <span className="text-muted-foreground">
                                                {m.dashboard_kilometers()}
                                            </span>
                                            <span className="font-mono font-medium tabular-nums">
                                                {formatNumber(
                                                    Number(value),
                                                    locale,
                                                )}{" "}
                                                km
                                            </span>
                                        </div>
                                    )}
                                />
                            }
                        />
                        <Bar
                            dataKey="kilometers"
                            fill="var(--color-kilometers)"
                            radius={[6, 6, 2, 2]}
                        />
                    </BarChart>
                </ChartContainer>
            </CardContent>
        </Card>
    );
}
