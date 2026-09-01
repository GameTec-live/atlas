//go:build linux

package ownership

import "os"

func Set(path string, uid, gid int) error {
	if uid < 0 && gid < 0 {
		return nil
	}
	return os.Chown(path, uid, gid)
}
