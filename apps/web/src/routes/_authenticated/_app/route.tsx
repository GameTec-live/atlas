import { createFileRoute, Link, Outlet } from "@tanstack/react-router";
import { Link2Icon, Settings2 } from "lucide-react";
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
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            viewBox="0 0 24 24"
                            className="dark:invert h-8"
                        >
                            <title>Logo</title>
                            <path d="M17.36,2.64L15.95,4.06C17.26,5.37 18,7.14 18,9A7,7 0 0,1 11,16C9.15,16 7.37,15.26 6.06,13.95L4.64,15.36C6.08,16.8 7.97,17.71 10,17.93V20H6V22H16V20H12V17.94C16.55,17.43 20,13.58 20,9C20,6.62 19.05,4.33 17.36,2.64" />
                            <g>
                                <path d="M11,3.5A5.5,5.5 0 0,0 5.5,9A5.5,5.5 0 0,0 11,14.5A5.5,5.5 0 0,0 16.5,9A5.5,5.5 0 0,0 11,3.5M11,5.5C12.94,5.5 14.5,7.07 14.5,9A3.5,3.5 0 0,1 11,12.5A3.5,3.5 0 0,1 7.5,9A3.5,3.5 0 0,1 11,5.5Z" />
                            </g>
                        </svg>
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
                                <NavigationMenuLink render={<Link to="/" />}>
                                    {m.cool_only_manatee_pout()}
                                </NavigationMenuLink>
                            </NavigationMenuItem>
                            <NavigationMenuItem>
                                <NavigationMenuTrigger>
                                    {m.factual_happy_falcon_arise()}
                                </NavigationMenuTrigger>
                                <NavigationMenuContent>
                                    <NavigationMenuLink
                                        render={<Link to="/" />}
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
