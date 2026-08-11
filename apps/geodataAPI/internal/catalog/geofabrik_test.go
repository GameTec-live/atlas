package catalog

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
)

func TestGeofabrikListFindAndCovering(t *testing.T) {
	t.Parallel()
	var headRequests atomic.Int32
	var server *httptest.Server
	server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, request *http.Request) {
		if request.Method == http.MethodHead {
			headRequests.Add(1)
			w.Header().Set("Content-Length", "1000")
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = fmt.Fprintf(w, `{
  "type":"FeatureCollection",
  "features":[
    {"properties":{"id":"europe","name":"Europe","urls":{"pbf":"%s/europe.pbf"}},"geometry":{"type":"Polygon","coordinates":[[[-20,30],[50,30],[50,75],[-20,75],[-20,30]]]}},
    {"properties":{"id":"austria","parent":"europe","name":"Austria","iso3166-1:alpha2":["AT"],"urls":{"pbf":"%s/austria.pbf"}},"geometry":{"type":"Polygon","coordinates":[[[9,46],[18,46],[18,50],[9,50],[9,46]]]}}
  ]
}`, server.URL, server.URL)
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
	if size := items[0].SizeBytes; size == nil || size.PBF != 1000 || size.GeocoderEstimate != 3000 || size.MapEstimate != 1500 || size.TotalEstimate != 5500 || size.TemporaryConversionEstimate != 10000 || size.PeakEstimate != 15500 {
		t.Fatalf("unexpected size estimate: %#v", size)
	}
	if _, err := catalog.List(context.Background(), "aus", "europe"); err != nil {
		t.Fatal(err)
	}
	if headRequests.Load() != 1 {
		t.Fatalf("PBF size was not cached: %d HEAD requests", headRequests.Load())
	}
	region, err := catalog.Find(context.Background(), "AUSTRIA")
	if err != nil || region.CountryCodes[0] != "AT" {
		t.Fatalf("unexpected find: %#v, %v", region, err)
	}
	region, err = catalog.Covering(context.Background(), model.Bounds{MinLongitude: 16, MinLatitude: 48, MaxLongitude: 16.5, MaxLatitude: 48.5})
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
	if !shape.covers(model.Bounds{MinLongitude: 1, MinLatitude: 1, MaxLongitude: 2, MaxLatitude: 2}) {
		t.Fatal("ordinary interior bounds should be covered")
	}
	if shape.covers(model.Bounds{MinLongitude: 4.5, MinLatitude: 4.5, MaxLongitude: 5.5, MaxLatitude: 5.5}) {
		t.Fatal("bounds inside a polygon hole must not be covered")
	}
}
