export type Coordinates = [number, number];
export type MapPoint = [number, number];

/** Decodes Valhalla precision-6 polylines into GeoJSON coordinate order. */
function decodeShape(shape: string): MapPoint[] {
    const points: MapPoint[] = [];
    let index = 0;
    let latitude = 0;
    let longitude = 0;

    const decodeValue = () => {
        let result = 0;
        let shift = 0;
        let byte: number;

        do {
            byte = shape.charCodeAt(index++) - 63;
            result |= (byte & 0x1f) << shift;
            shift += 5;
        } while (byte >= 0x20 && index < shape.length);

        return result & 1 ? ~(result >> 1) : result >> 1;
    };

    while (index < shape.length) {
        latitude += decodeValue();
        longitude += decodeValue();
        points.push([longitude / 1e6, latitude / 1e6]);
    }

    return points;
}

export function decodeRouteShapes(legs: ReadonlyArray<{ shape: string }>) {
    return legs.flatMap((leg, index) => {
        const points = decodeShape(leg.shape);
        return index === 0 ? points : points.slice(1);
    });
}

export function toMapPoint([latitude, longitude]: Coordinates): MapPoint {
    return [longitude, latitude];
}
