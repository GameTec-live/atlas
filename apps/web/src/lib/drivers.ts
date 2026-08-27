import type { LiveDriver } from "@/hooks/use-live-drivers";
import { m } from "@/paraglide/messages";

export const driverStateBackgrounds = {
    free: "bg-emerald-500",
    onTheWay: "bg-blue-500",
    occupied: "bg-amber-500",
    away: "bg-slate-400",
} satisfies Record<LiveDriver["state"], string>;

export const driverStateLabels = {
    free: m.dizzy_silly_gopher_boil(),
    onTheWay: m.fit_mild_halibut_grace(),
    occupied: m.inclusive_bright_halibut_flop(),
    away: m.small_house_grizzly_view(),
} satisfies Record<LiveDriver["state"], string>;
