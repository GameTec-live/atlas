package api

import (
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"time"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/catalog"
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/config"
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
	cfg     config.Config
}

func NewRouter(manager *jobs.Manager, regionCatalog catalog.Catalog, dataStore *store.Store, cfg config.Config) http.Handler {
	router := gin.New()
	router.Use(gin.Logger(), gin.Recovery())
	h := &handler{manager: manager, catalog: regionCatalog, store: dataStore, cfg: cfg}

	router.GET("/healthz", func(c *gin.Context) { c.JSON(http.StatusOK, gin.H{"status": "ok"}) })
	api := router.Group("/api/v1")
	api.GET("/options", h.listOptions)
	api.GET("/options/products", h.listProducts)
	api.GET("/installed", h.listInstalled)
	api.POST("/downloads/name", h.downloadByName)
	api.POST("/downloads/bbox", h.downloadByBBox)
	api.GET("/downloads", h.listJobs)
	api.GET("/downloads/ws", h.jobsWebsocket)
	api.GET("/downloads/:id", h.getJob)
	api.DELETE("/downloads/:id", h.cancelJob)
	api.DELETE("/data/:id", h.deleteDataset)

	return router
}

func (h *handler) listOptions(c *gin.Context) {
	regions, err := h.catalog.List(c.Request.Context(), c.Query("q"), c.Query("parent"))
	if err != nil {
		fail(c, http.StatusBadGateway, "catalog_unavailable", err.Error())
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": regions, "count": len(regions)})
}

func (h *handler) listProducts(c *gin.Context) {
	osmiumPath, osmiumErr := exec.LookPath(h.cfg.OsmiumBinary)
	packgenPath, packgenErr := exec.LookPath(h.cfg.PackgenBinary)
	javaPath, javaErr := exec.LookPath(h.cfg.JavaBinary)
	_, jarErr := os.Stat(h.cfg.PlanetilerJar)
	c.JSON(http.StatusOK, gin.H{
		"products": []gin.H{
			{"id": model.ProductPBF, "available": true, "description": "Raw routing-server OSM PBF"},
			{"id": model.ProductGeocoder, "available": packgenErr == nil, "description": "geocoder-go SQLite pack"},
			{"id": model.ProductMap, "available": javaErr == nil && h.cfg.PlanetilerJar != "" && jarErr == nil, "description": "Combined map.pmtiles archive"},
		},
		"tools": gin.H{
			"osmium":         gin.H{"available": osmiumErr == nil, "path": osmiumPath},
			"packgen":        gin.H{"available": packgenErr == nil, "path": packgenPath},
			"java":           gin.H{"available": javaErr == nil, "path": javaPath},
			"planetiler_jar": gin.H{"available": h.cfg.PlanetilerJar != "" && jarErr == nil, "path": h.cfg.PlanetilerJar},
		},
	})
}

func (h *handler) listInstalled(c *gin.Context) {
	datasets := h.store.Datasets()
	c.JSON(http.StatusOK, gin.H{"items": datasets, "count": len(datasets)})
}

type nameRequest struct {
	Name     string          `json:"name" binding:"required"`
	Products []model.Product `json:"products"`
}

func (h *handler) downloadByName(c *gin.Context) {
	var request nameRequest
	if err := c.ShouldBindJSON(&request); err != nil {
		fail(c, http.StatusBadRequest, "invalid_request", err.Error())
		return
	}
	if err := validateProducts(request.Products); err != "" {
		fail(c, http.StatusBadRequest, "invalid_products", err)
		return
	}
	job, err := h.manager.StartByName(c.Request.Context(), request.Name, request.Products)
	if err != nil {
		fail(c, http.StatusConflict, "cannot_start_download", err.Error())
		return
	}
	c.Header("Location", "/api/v1/downloads/"+job.ID)
	c.JSON(http.StatusAccepted, job)
}

type bboxRequest struct {
	ID       string          `json:"id"`
	BBox     *model.Bounds   `json:"bbox" binding:"required"`
	Products []model.Product `json:"products"`
}

func (h *handler) downloadByBBox(c *gin.Context) {
	var request bboxRequest
	if err := c.ShouldBindJSON(&request); err != nil {
		fail(c, http.StatusBadRequest, "invalid_request", err.Error())
		return
	}
	if request.BBox == nil || !request.BBox.Valid() {
		fail(c, http.StatusBadRequest, "invalid_bbox", "bbox must have west < east and south < north")
		return
	}
	if err := validateProducts(request.Products); err != "" {
		fail(c, http.StatusBadRequest, "invalid_products", err)
		return
	}
	job, err := h.manager.StartByBounds(c.Request.Context(), request.ID, *request.BBox, request.Products)
	if err != nil {
		fail(c, http.StatusConflict, "cannot_start_download", err.Error())
		return
	}
	c.Header("Location", "/api/v1/downloads/"+job.ID)
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
		fail(c, http.StatusNotFound, "not_found", "download job not found")
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
	c.Header("Location", "/api/v1/downloads/"+job.ID)
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

func validateProducts(products []model.Product) string {
	for _, product := range products {
		if product != model.ProductPBF && product != model.ProductGeocoder && product != model.ProductMap {
			return "products may only contain pbf, geocoder, and map"
		}
	}
	return ""
}

func fail(c *gin.Context, status int, code, message string) {
	c.AbortWithStatusJSON(status, gin.H{"error": gin.H{"code": code, "message": message}})
}
