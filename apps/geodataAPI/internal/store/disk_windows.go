//go:build windows

package store

import (
	"path/filepath"

	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
	"golang.org/x/sys/windows"
)

func diskSpace(path string) (model.DiskSpace, error) {
	absolute, err := filepath.Abs(path)
	if err != nil {
		return model.DiskSpace{}, err
	}
	directory, err := windows.UTF16PtrFromString(absolute)
	if err != nil {
		return model.DiskSpace{}, err
	}
	var free, total uint64
	if err := windows.GetDiskFreeSpaceEx(directory, &free, &total, nil); err != nil {
		return model.DiskSpace{}, err
	}
	return model.DiskSpace{FreeBytes: free, TotalBytes: total}, nil
}
