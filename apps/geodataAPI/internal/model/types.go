package model

import "time"

type Bounds struct {
	MinLongitude float64 `json:"minLongitude" binding:"gte=-180,lte=180"`
	MinLatitude  float64 `json:"minLatitude" binding:"gte=-90,lte=90"`
	MaxLongitude float64 `json:"maxLongitude" binding:"gte=-180,lte=180"`
	MaxLatitude  float64 `json:"maxLatitude" binding:"gte=-90,lte=90"`
}

func (b Bounds) Valid() bool {
	return b.MinLongitude >= -180 && b.MaxLongitude <= 180 && b.MinLatitude >= -90 && b.MaxLatitude <= 90 &&
		b.MinLongitude < b.MaxLongitude && b.MinLatitude < b.MaxLatitude
}

type ArtifactKind string

const (
	ArtifactPBF      ArtifactKind = "pbf"
	ArtifactGeocoder ArtifactKind = "geocoder"
	ArtifactMap      ArtifactKind = "map"
)

type Region struct {
	ID           string                `json:"id"`
	Name         string                `json:"name"`
	Parent       string                `json:"parent,omitempty"`
	CountryCodes []string              `json:"country_codes,omitempty"`
	PBFURL       string                `json:"pbf_url"`
	Bounds       *Bounds               `json:"bounds,omitempty"`
	SizeBytes    *EstimatedDatasetSize `json:"size_bytes,omitempty"`
}

type EstimatedDatasetSize struct {
	PBF                         int64 `json:"pbf"`
	GeocoderEstimate            int64 `json:"geocoder_estimate"`
	MapEstimate                 int64 `json:"map_estimate"`
	TotalEstimate               int64 `json:"total_estimate"`
	TemporaryConversionEstimate int64 `json:"temporary_conversion_estimate"`
	PeakEstimate                int64 `json:"peak_estimate"`
}

type DiskSpace struct {
	FreeBytes  uint64 `json:"free_bytes"`
	TotalBytes uint64 `json:"total_bytes"`
}

type Artifact struct {
	Kind       ArtifactKind `json:"kind"`
	Path       string       `json:"path"`
	Size       int64        `json:"size_bytes"`
	ModifiedAt time.Time    `json:"modified_at"`
}

type Dataset struct {
	ID                 string     `json:"id"`
	Name               string     `json:"name"`
	State              string     `json:"state"`
	SourceType         string     `json:"source_type"`
	SourceRegion       string     `json:"source_region,omitempty"`
	SourceURL          string     `json:"source_url"`
	CountryCode        string     `json:"country_code,omitempty"`
	Bounds             *Bounds    `json:"bounds,omitempty"`
	ExcludeRoads       bool       `json:"exclude_roads"`
	SourceETag         string     `json:"source_etag,omitempty"`
	SourceLastModified string     `json:"source_last_modified,omitempty"`
	LastCheckedAt      *time.Time `json:"last_checked_at,omitempty"`
	UpdatedAt          *time.Time `json:"updated_at,omitempty"`
	Artifacts          []Artifact `json:"artifacts"`
	InstalledAt        time.Time  `json:"installed_at"`
}

type JobState string

const (
	JobQueued    JobState = "queued"
	JobRunning   JobState = "running"
	JobCompleted JobState = "completed"
	JobFailed    JobState = "failed"
	JobCancelled JobState = "cancelled"
)

type Job struct {
	ID         string     `json:"id"`
	Operation  string     `json:"operation"`
	DatasetID  string     `json:"dataset_id"`
	State      JobState   `json:"state"`
	Stage      string     `json:"stage"`
	Progress   float64    `json:"progress"`
	BytesDone  int64      `json:"bytes_done,omitempty"`
	BytesTotal int64      `json:"bytes_total,omitempty"`
	Error      string     `json:"error,omitempty"`
	CreatedAt  time.Time  `json:"created_at"`
	StartedAt  *time.Time `json:"started_at,omitempty"`
	FinishedAt *time.Time `json:"finished_at,omitempty"`
	Request    JobRequest `json:"request"`
}

type JobRequest struct {
	Name         string  `json:"name,omitempty"`
	DatasetID    string  `json:"dataset_id,omitempty"`
	SourceType   string  `json:"source_type,omitempty"`
	Bounds       *Bounds `json:"bbox,omitempty"`
	ExcludeRoads bool    `json:"excludeRoads"`
	Region       *Region `json:"region,omitempty"`
}

type State struct {
	Version  int                `json:"version"`
	Datasets map[string]Dataset `json:"datasets"`
	Jobs     map[string]Job     `json:"jobs"`
}
