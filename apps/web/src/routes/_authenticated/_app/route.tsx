import { createFileRoute, Link, Outlet } from "@tanstack/react-router";
import { Link2Icon, Settings2 } from "lucide-react";
import { AtlasLogo } from "@/components/atlas-logo";
import LanguageToggle from "@/components/language-toggle";
import { ModeToggle } from "@/components/mode-toggle";
import SignoutButton from "@/components/signout-button";
import {
    NavigationMenu,
    NavigationMenuContent,
    NavigationMenuItem,
    NavigationMenuLink,
    NavigationMenuList,
    NavigationMenuTrigger,
} from "@/components/ui/navigation-menu";
import { m } from "@/paraglide/messages";

export const Route = createFileRoute("/_authenticated/_app")({
    component: RouteComponent,
});

function RouteComponent() {
    return (
        <div className="flex h-svh flex-col">
            <nav className="flex shrink-0 flex-row flex-wrap items-center justify-between gap-2 border-b p-2">
                <div className="flex flex-row items-center gap-2">
                    <Link to="/">
                        <AtlasLogo className="h-8 dark:invert" />
                    </Link>
                    <NavigationMenu>
                        <NavigationMenuList>
                            <NavigationMenuItem>
                                <NavigationMenuLink render={<Link to="/" />}>
                                    {m.plain_topical_ant_compose()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuLink
                                    render={<Link to="/realtime" />}
                                >
                                    {m.simple_awful_coyote_quiz()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuLink
                                    render={<Link to="/jobs" />}
                                >
                                    {m.keen_plane_bobcat_value()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuLink
                                    render={<Link to="/fleet" />}
                                >
                                    {m.cool_only_manatee_pout()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuLink
                                    render={<Link to="/logbook" />}
                                >
                                    {m.logbook_title()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuTrigger>
                                    {m.factual_happy_falcon_arise()}
                                </NavigationMenuTrigger>
                                <NavigationMenuContent>
                                    <NavigationMenuLink
                                        render={
                                            <Link to="/settings/shortnames" />
                                        }
                                    >
                                        <Link2Icon />
                                        {m.long_topical_wolf_embrace()}
                                    </NavigationMenuLink>
                                    <NavigationMenuLink
                                        render={<Link to="/settings" />}
                                    >
                                        <Settings2 />
                                        {m.noisy_topical_camel_bloom()}
                                    </NavigationMenuLink>
                                </NavigationMenuContent>
                            </NavigationMenuItem>
                        </NavigationMenuList>
                    </NavigationMenu>
                </div>
                <div className="flex flex-row items-center gap-2">
                    <LanguageToggle />
                    <ModeToggle />
                    <SignoutButton />
                </div>
            </nav>
            <div className="min-h-0 flex-1">
                <Outlet />
            </div>
        </div>
    );
}
