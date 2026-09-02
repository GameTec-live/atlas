//go:build !linux

package timezone

func syncDirectory(string) error {
	return nil
}
