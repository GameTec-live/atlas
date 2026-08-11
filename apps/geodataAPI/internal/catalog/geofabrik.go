package catalog

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
)

type Catalog interface {
	List(context.Context, string, string) ([]model.Region, error)
	Find(context.Context, string) (model.Region, error)
	Covering(context.Context, model.Bounds) (model.Region, error)
}

type Geofabrik struct {
	url        string
	ttl        time.Duration
	client     *http.Client
	mu         sync.Mutex
	loadedAt   time.Time
	regions    []model.Region
	envelopes  map[string]model.Bounds
	geometries map[string]geometry
}

func NewGeofabrik(url string, ttl time.Duration, client *http.Client) *Geofabrik {
	if client == nil {
		client = &http.Client{Timeout: 30 * time.Second}
	}
	return &Geofabrik{url: url, ttl: ttl, client: client}
}

func (g *Geofabrik) List(ctx context.Context, query, parent string) ([]model.Region, error) {
	if err := g.load(ctx); err != nil {
		return nil, err
	}
	query = strings.ToLower(strings.TrimSpace(query))
	parent = strings.ToLower(strings.TrimSpace(parent))

	g.mu.Lock()
	defer g.mu.Unlock()
	result := make([]model.Region, 0, len(g.regions))
	for _, region := range g.regions {
		if parent != "" && strings.ToLower(region.Parent) != parent {
			continue
		}
		if query != "" && !strings.Contains(strings.ToLower(region.ID), query) && !strings.Contains(strings.ToLower(region.Name), query) {
			continue
		}
		result = append(result, region)
	}
	return result, nil
}

func (g *Geofabrik) Find(ctx context.Context, name string) (model.Region, error) {
	if err := g.load(ctx); err != nil {
		return model.Region{}, err
	}
	needle := strings.ToLower(strings.TrimSpace(name))
	g.mu.Lock()
	defer g.mu.Unlock()
	for _, region := range g.regions {
		if strings.ToLower(region.ID) == needle || strings.ToLower(region.Name) == needle {
			return region, nil
		}
	}
	return model.Region{}, fmt.Errorf("region %q not found", name)
}

func (g *Geofabrik) Covering(ctx context.Context, bounds model.Bounds) (model.Region, error) {
	if err := g.load(ctx); err != nil {
		return model.Region{}, err
	}
	g.mu.Lock()
	defer g.mu.Unlock()

	bestArea := math.Inf(1)
	var best model.Region
	for _, region := range g.regions {
		envelope, ok := g.envelopes[region.ID]
		shape, hasShape := g.geometries[region.ID]
		if !ok || !hasShape || envelope.MinLongitude > bounds.MinLongitude || envelope.MinLatitude > bounds.MinLatitude || envelope.MaxLongitude < bounds.MaxLongitude || envelope.MaxLatitude < bounds.MaxLatitude || !shape.covers(bounds) {
			continue
		}
		area := (envelope.MaxLongitude - envelope.MinLongitude) * (envelope.MaxLatitude - envelope.MinLatitude)
		if area < bestArea {
			bestArea = area
			best = region
		}
	}
	if best.ID == "" {
		return model.Region{}, fmt.Errorf("no Geofabrik extract covers the bounding box")
	}
	return best, nil
}

func (g *Geofabrik) load(ctx context.Context) error {
	g.mu.Lock()
	if len(g.regions) > 0 && time.Since(g.loadedAt) < g.ttl {
		g.mu.Unlock()
		return nil
	}
	g.mu.Unlock()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, g.url, nil)
	if err != nil {
		return fmt.Errorf("create catalog request: %w", err)
	}
	req.Header.Set("User-Agent", "atlas-geodata-api/1.0")
	resp, err := g.client.Do(req)
	if err != nil {
		return fmt.Errorf("fetch Geofabrik catalog: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		_, _ = io.Copy(io.Discard, resp.Body)
		return fmt.Errorf("fetch Geofabrik catalog: HTTP %d", resp.StatusCode)
	}

	var document featureCollection
	decoder := json.NewDecoder(io.LimitReader(resp.Body, 64<<20))
	if err := decoder.Decode(&document); err != nil {
		return fmt.Errorf("decode Geofabrik catalog: %w", err)
	}

	regions := make([]model.Region, 0, len(document.Features))
	envelopes := make(map[string]model.Bounds, len(document.Features))
	geometries := make(map[string]geometry, len(document.Features))
	for _, feature := range document.Features {
		if feature.Properties.ID == "" || feature.Properties.URLs.PBF == "" {
			continue
		}
		region := model.Region{
			ID:           feature.Properties.ID,
			Name:         feature.Properties.Name,
			Parent:       feature.Properties.Parent,
			CountryCodes: feature.Properties.CountryCodes,
			PBFURL:       feature.Properties.URLs.PBF,
		}
		if envelope, ok := geometryEnvelope(feature.Geometry.Coordinates); ok {
			region.Bounds = &envelope
			envelopes[region.ID] = envelope
		}
		if shape, ok := parseGeometry(feature.Geometry.Type, feature.Geometry.Coordinates); ok {
			geometries[region.ID] = shape
		}
		regions = append(regions, region)
	}
	sort.Slice(regions, func(i, j int) bool { return regions[i].Name < regions[j].Name })

	g.mu.Lock()
	g.regions = regions
	g.envelopes = envelopes
	g.geometries = geometries
	g.loadedAt = time.Now()
	g.mu.Unlock()
	return nil
}

type featureCollection struct {
	Features []struct {
		Properties struct {
			ID           string   `json:"id"`
			Name         string   `json:"name"`
			Parent       string   `json:"parent"`
			CountryCodes []string `json:"iso3166-1:alpha2"`
			URLs         struct {
				PBF string `json:"pbf"`
			} `json:"urls"`
		} `json:"properties"`
		Geometry struct {
			Type        string `json:"type"`
			Coordinates any    `json:"coordinates"`
		} `json:"geometry"`
	} `json:"features"`
}

type point struct{ lon, lat float64 }
type polygon [][]point
type geometry []polygon

func parseGeometry(kind string, coordinates any) (geometry, bool) {
	items, ok := coordinates.([]any)
	if !ok {
		return nil, false
	}
	var result geometry
	switch kind {
	case "Polygon":
		if parsed, ok := parsePolygon(items); ok {
			result = append(result, parsed)
		}
	case "MultiPolygon":
		for _, item := range items {
			polygonItems, ok := item.([]any)
			if !ok {
				continue
			}
			if parsed, ok := parsePolygon(polygonItems); ok {
				result = append(result, parsed)
			}
		}
	}
	return result, len(result) > 0
}

func parsePolygon(items []any) (polygon, bool) {
	var result polygon
	for _, item := range items {
		ringItems, ok := item.([]any)
		if !ok {
			continue
		}
		ring := make([]point, 0, len(ringItems))
		for _, coordinate := range ringItems {
			pair, ok := coordinate.([]any)
			if !ok || len(pair) < 2 {
				continue
			}
			lon, lonOK := pair[0].(float64)
			lat, latOK := pair[1].(float64)
			if lonOK && latOK {
				ring = append(ring, point{lon: lon, lat: lat})
			}
		}
		if len(ring) >= 3 {
			result = append(result, ring)
		}
	}
	return result, len(result) > 0
}

func (g geometry) covers(bounds model.Bounds) bool {
	checks := []point{
		{bounds.MinLongitude, bounds.MinLatitude}, {bounds.MinLongitude, bounds.MaxLatitude},
		{bounds.MaxLongitude, bounds.MinLatitude}, {bounds.MaxLongitude, bounds.MaxLatitude},
		{(bounds.MinLongitude + bounds.MaxLongitude) / 2, (bounds.MinLatitude + bounds.MaxLatitude) / 2},
	}
	for _, check := range checks {
		contained := false
		for _, candidate := range g {
			if candidate.contains(check) {
				contained = true
				break
			}
		}
		if !contained {
			return false
		}
	}
	return true
}

func (p polygon) contains(candidate point) bool {
	if len(p) == 0 || !ringContains(p[0], candidate) {
		return false
	}
	for _, hole := range p[1:] {
		if ringContains(hole, candidate) {
			return false
		}
	}
	return true
}

func ringContains(ring []point, candidate point) bool {
	inside := false
	previous := len(ring) - 1
	for current := range ring {
		a, b := ring[previous], ring[current]
		if pointOnSegment(candidate, a, b) {
			return true
		}
		if (a.lat > candidate.lat) != (b.lat > candidate.lat) &&
			candidate.lon < (b.lon-a.lon)*(candidate.lat-a.lat)/(b.lat-a.lat)+a.lon {
			inside = !inside
		}
		previous = current
	}
	return inside
}

func pointOnSegment(candidate, a, b point) bool {
	const epsilon = 1e-10
	cross := (candidate.lat-a.lat)*(b.lon-a.lon) - (candidate.lon-a.lon)*(b.lat-a.lat)
	if math.Abs(cross) > epsilon {
		return false
	}
	return candidate.lon >= math.Min(a.lon, b.lon)-epsilon && candidate.lon <= math.Max(a.lon, b.lon)+epsilon &&
		candidate.lat >= math.Min(a.lat, b.lat)-epsilon && candidate.lat <= math.Max(a.lat, b.lat)+epsilon
}

func geometryEnvelope(coordinates any) (model.Bounds, bool) {
	bounds := model.Bounds{MinLongitude: math.Inf(1), MinLatitude: math.Inf(1), MaxLongitude: math.Inf(-1), MaxLatitude: math.Inf(-1)}
	var found bool
	var visit func(any)
	visit = func(value any) {
		items, ok := value.([]any)
		if !ok {
			return
		}
		if len(items) >= 2 {
			lon, lonOK := items[0].(float64)
			lat, latOK := items[1].(float64)
			if lonOK && latOK {
				bounds.MinLongitude = math.Min(bounds.MinLongitude, lon)
				bounds.MinLatitude = math.Min(bounds.MinLatitude, lat)
				bounds.MaxLongitude = math.Max(bounds.MaxLongitude, lon)
				bounds.MaxLatitude = math.Max(bounds.MaxLatitude, lat)
				found = true
				return
			}
		}
		for _, item := range items {
			visit(item)
		}
	}
	visit(coordinates)
	return bounds, found
}
