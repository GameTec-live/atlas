//go:build linux

package store

import (
	"github.com/GameTec-live/atlas/apps/geodataAPI/internal/model"
	"golang.org/x/sys/unix"
)

func diskSpace(path string) (model.DiskSpace, error) {
	var stats unix.Statfs_t
	if err := unix.Statfs(path, &stats); err != nil {
		return model.DiskSpace{}, err
	}
	blockSize := uint64(stats.Bsize)
	return model.DiskSpace{
		FreeBytes:  stats.Bavail * blockSize,
		TotalBytes: stats.Blocks * blockSize,
	}, nil
}
