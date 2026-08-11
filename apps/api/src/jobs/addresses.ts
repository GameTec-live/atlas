import { reverseGeocode } from "../geoservices/geocoder";

interface JobLocations {
    from: [number, number];
    to: [number, number] | null;
}

export const withReverseGeocodedAddresses = async <T extends JobLocations>(
    jobs: T[],
) => {
    const addresses = new Map<string, Promise<string | undefined>>();
    const resolveAddress = (coordinates: [number, number]) => {
        const key = coordinates.join(",");
        const pending = addresses.get(key);
        if (pending) return pending;

        const address = reverseGeocode(coordinates);
        addresses.set(key, address);
        return address;
    };

    return Promise.all(
        jobs.map(async (job) => {
            const [fromAddress, toAddress] = await Promise.all([
                resolveAddress(job.from),
                job.to ? resolveAddress(job.to) : null,
            ]);

            return {
                ...job,
                fromAddress: fromAddress ?? null,
                toAddress: toAddress ?? null,
            };
        }),
    );
};

export const withReverseGeocodedAddress = async <T extends JobLocations>(
    job: T,
) => {
    const [enrichedJob] = await withReverseGeocodedAddresses([job]);
    if (!enrichedJob) throw new Error("Failed to enrich job addresses");
    return enrichedJob;
};
