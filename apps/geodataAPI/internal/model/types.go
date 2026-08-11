package model

import "time"

type Bounds struct {
	West  float64 `json:"west" binding:"gte=-180,lte=180"`
	South float64 `json:"south" binding:"gte=-90,lte=90"`
	East  float64 `json:"east" binding:"gte=-180,lte=180"`
	North float64 `json:"north" binding:"gte=-90,lte=90"`
}

func (b Bounds) Valid() bool {
	return b.West >= -180 && b.East <= 180 && b.South >= -90 && b.North <= 90 &&
		b.West < b.East && b.South < b.North
}

type Product string

const (
	ProductPBF      Product = "pbf"
	ProductGeocoder Product = "geocoder"
	ProductMap      Product = "map"
)

type Region struct {
	ID           string   `json:"id"`
	Name         string   `json:"name"`
	Parent       string   `json:"parent,omitempty"`
	CountryCodes []string `json:"country_codes,omitempty"`
	PBFURL       string   `json:"pbf_url"`
	Bounds       *Bounds  `json:"bounds,omitempty"`
}

type Artifact struct {
	Kind       Product   `json:"kind"`
	Path       string    `json:"path"`
	Size       int64     `json:"size_bytes"`
	ModifiedAt time.Time `json:"modified_at"`
}

type Dataset struct {
	ID           string     `json:"id"`
	Name         string     `json:"name"`
	State        string     `json:"state"`
	SourceType   string     `json:"source_type"`
	SourceRegion string     `json:"source_region,omitempty"`
	SourceURL    string     `json:"source_url"`
	CountryCode  string     `json:"country_code,omitempty"`
	Bounds       *Bounds    `json:"bounds,omitempty"`
	Products     []Product  `json:"products"`
	Artifacts    []Artifact `json:"artifacts"`
	InstalledAt  time.Time  `json:"installed_at"`
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
	Name       string    `json:"name,omitempty"`
	DatasetID  string    `json:"dataset_id,omitempty"`
	Bounds     *Bounds   `json:"bbox,omitempty"`
	Products   []Product `json:"products,omitempty"`
	Region     *Region   `json:"region,omitempty"`
	DeleteData bool      `json:"delete_data,omitempty"`
}

type State struct {
	Version  int                `json:"version"`
	Datasets map[string]Dataset `json:"datasets"`
	Jobs     map[string]Job     `json:"jobs"`
}
