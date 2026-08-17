package api

import (
	"errors"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/catalog"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/jobs"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
)

type handler struct {
	manager *jobs.Manager
	catalog catalog.Catalog
	store   *store.Store
}

func NewRouter(manager *jobs.Manager, regionCatalog catalog.Catalog, dataStore *store.Store) http.Handler {
	router := gin.New()
	router.Use(gin.Logger(), gin.Recovery())
	h := &handler{manager: manager, catalog: regionCatalog, store: dataStore}

	router.GET("/healthz", func(c *gin.Context) { c.JSON(http.StatusOK, gin.H{"status": "ok"}) })
	api := router.Group("/api/v1")
	api.GET("/catalog", h.listCatalog)
	api.GET("/datasets", h.listDatasets)
	api.POST("/datasets", h.installDataset)
	api.POST("/datasets/:id/update", h.updateDataset)
	api.DELETE("/datasets/:id", h.deleteDataset)
	api.GET("/jobs", h.listJobs)
	api.GET("/jobs/ws", h.jobsWebsocket)
	api.GET("/jobs/:id", h.getJob)
	api.DELETE("/jobs/:id", h.cancelJob)

	return router
}

func (h *handler) listCatalog(c *gin.Context) {
	regions, err := h.catalog.List(c.Request.Context(), c.Query("q"), c.Query("parent"))
	if err != nil {
		fail(c, http.StatusBadGateway, "catalog_unavailable", err.Error())
		return
	}
	h.listResponse(c, regions, len(regions))
}

func (h *handler) listDatasets(c *gin.Context) {
	datasets := h.store.Datasets()
	h.listResponse(c, datasets, len(datasets))
}

func (h *handler) listResponse(c *gin.Context, items any, count int) {
	response := gin.H{"items": items, "count": count}
	if diskSpace, err := h.store.DiskSpace(); err == nil {
		response["disk_space"] = diskSpace
	}
	c.JSON(http.StatusOK, response)
}

type installRequest struct {
	ID           string        `json:"id"`
	URL          string        `json:"url"`
	BBox         *model.Bounds `json:"bbox"`
	ExcludeRoads bool          `json:"excludeRoads"`
}

func (h *handler) installDataset(c *gin.Context) {
	var request installRequest
	if err := c.ShouldBindJSON(&request); err != nil {
		fail(c, http.StatusBadRequest, "invalid_request", err.Error())
		return
	}
	hasID := strings.TrimSpace(request.ID) != ""
	hasURL := strings.TrimSpace(request.URL) != ""
	hasBBox := request.BBox != nil
	if (!hasBBox && hasID == hasURL) || (hasBBox && hasURL) {
		fail(c, http.StatusBadRequest, "invalid_source", "provide exactly one of catalog id, url, or bbox")
		return
	}
	if hasBBox && !request.BBox.Valid() {
		fail(c, http.StatusBadRequest, "invalid_bbox", "bbox must have minLongitude < maxLongitude and minLatitude < maxLatitude")
		return
	}
	job, err := h.manager.Install(c.Request.Context(), request.ID, request.URL, request.BBox, request.ExcludeRoads)
	if err != nil {
		status := http.StatusConflict
		if errors.Is(err, jobs.ErrInvalidInstallSource) {
			status = http.StatusBadRequest
		}
		fail(c, status, "cannot_install_dataset", err.Error())
		return
	}
	c.Header("Location", "/api/v1/jobs/"+job.ID)
	c.JSON(http.StatusAccepted, job)
}

func (h *handler) listJobs(c *gin.Context) {
	active, _ := strconv.ParseBool(c.DefaultQuery("active", "false"))
	items := h.manager.Jobs(active)
	c.JSON(http.StatusOK, gin.H{"items": items, "count": len(items)})
}

func (h *handler) getJob(c *gin.Context) {
	job, ok := h.manager.Job(c.Param("id"))
	if !ok {
		fail(c, http.StatusNotFound, "not_found", "job not found")
		return
	}
	c.JSON(http.StatusOK, job)
}

func (h *handler) cancelJob(c *gin.Context) {
	job, err := h.manager.Cancel(c.Param("id"))
	if err != nil {
		fail(c, http.StatusConflict, "cannot_cancel", err.Error())
		return
	}
	c.JSON(http.StatusAccepted, job)
}

func (h *handler) deleteDataset(c *gin.Context) {
	job, err := h.manager.Delete(c.Param("id"))
	if err != nil {
		fail(c, http.StatusNotFound, "cannot_delete", err.Error())
		return
	}
	c.Header("Location", "/api/v1/jobs/"+job.ID)
	c.JSON(http.StatusAccepted, job)
}

func (h *handler) updateDataset(c *gin.Context) {
	job, err := h.manager.Update(c.Param("id"))
	if err != nil {
		status := http.StatusConflict
		if errors.Is(err, jobs.ErrDatasetNotFound) {
			status = http.StatusNotFound
		}
		fail(c, status, "cannot_update", err.Error())
		return
	}
	c.Header("Location", "/api/v1/jobs/"+job.ID)
	c.JSON(http.StatusAccepted, job)
}

var upgrader = websocket.Upgrader{
	HandshakeTimeout: 5 * time.Second,
	CheckOrigin: func(request *http.Request) bool {
		origin := request.Header.Get("Origin")
		if origin == "" {
			return true
		}
		parsed, err := url.Parse(origin)
		return err == nil && strings.EqualFold(parsed.Host, request.Host)
	},
}

func (h *handler) jobsWebsocket(c *gin.Context) {
	connection, err := upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		return
	}
	defer connection.Close()
	updates, unsubscribe := h.manager.Subscribe()
	defer unsubscribe()

	if err := connection.WriteJSON(gin.H{"type": "snapshot", "jobs": h.manager.Jobs(false)}); err != nil {
		return
	}
	ping := time.NewTicker(25 * time.Second)
	defer ping.Stop()
	for {
		select {
		case <-c.Request.Context().Done():
			return
		case job, ok := <-updates:
			if !ok || connection.WriteJSON(gin.H{"type": "job", "job": job}) != nil {
				return
			}
		case <-ping.C:
			if connection.WriteControl(websocket.PingMessage, nil, time.Now().Add(5*time.Second)) != nil {
				return
			}
		}
	}
}

func fail(c *gin.Context, status int, code, message string) {
	c.AbortWithStatusJSON(status, gin.H{"error": gin.H{"code": code, "message": message}})
}
