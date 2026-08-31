//go:build linux

package reset

import "syscall"

func syncFilesystems() {
	syscall.Sync()
}
