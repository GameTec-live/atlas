package catalog

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
)

func TestGeofabrikListFindAndCovering(t *testing.T) {
	t.Parallel()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{
  "type":"FeatureCollection",
  "features":[
    {"properties":{"id":"europe","name":"Europe","urls":{"pbf":"https://example/europe.pbf"}},"geometry":{"type":"Polygon","coordinates":[[[-20,30],[50,30],[50,75],[-20,75],[-20,30]]]}},
    {"properties":{"id":"austria","parent":"europe","name":"Austria","iso3166-1:alpha2":["AT"],"urls":{"pbf":"https://example/austria.pbf"}},"geometry":{"type":"Polygon","coordinates":[[[9,46],[18,46],[18,50],[9,50],[9,46]]]}}
  ]
}`))
	}))
	defer server.Close()

	catalog := NewGeofabrik(server.URL, time.Hour, server.Client())
	items, err := catalog.List(context.Background(), "aus", "europe")
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 1 || items[0].ID != "austria" {
		t.Fatalf("unexpected list: %#v", items)
	}
	region, err := catalog.Find(context.Background(), "AUSTRIA")
	if err != nil || region.CountryCodes[0] != "AT" {
		t.Fatalf("unexpected find: %#v, %v", region, err)
	}
	region, err = catalog.Covering(context.Background(), model.Bounds{West: 16, South: 48, East: 16.5, North: 48.5})
	if err != nil || region.ID != "austria" {
		t.Fatalf("unexpected covering region: %#v, %v", region, err)
	}
}

func TestGeometryCoversHonorsHoles(t *testing.T) {
	t.Parallel()
	coordinates := []any{
		[]any{
			[]any{0.0, 0.0}, []any{10.0, 0.0}, []any{10.0, 10.0}, []any{0.0, 10.0}, []any{0.0, 0.0},
		},
		[]any{
			[]any{4.0, 4.0}, []any{6.0, 4.0}, []any{6.0, 6.0}, []any{4.0, 6.0}, []any{4.0, 4.0},
		},
	}
	shape, ok := parseGeometry("Polygon", coordinates)
	if !ok {
		t.Fatal("geometry was not parsed")
	}
	if !shape.covers(model.Bounds{West: 1, South: 1, East: 2, North: 2}) {
		t.Fatal("ordinary interior bounds should be covered")
	}
	if shape.covers(model.Bounds{West: 4.5, South: 4.5, East: 5.5, North: 5.5}) {
		t.Fatal("bounds inside a polygon hole must not be covered")
	}
}
