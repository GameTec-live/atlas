import { useRouter } from "@tanstack/react-router";
import { authClient } from "@/lib/auth-client";
import { queryClient } from "@/lib/query-client";
import { m } from "@/paraglide/messages";
import { Button } from "./ui/button";

export default function SignoutButton() {
    const router = useRouter();
    return (
        <Button
            variant="outline"
            onClick={async () => {
                await authClient.signOut();
                queryClient.clear();
                await router.invalidate();
            }}
        >
            {m.antsy_fuzzy_platypus_trim()}
        </Button>
    );
}
