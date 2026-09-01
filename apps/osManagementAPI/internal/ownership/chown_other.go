//go:build !linux

package ownership

func Set(_ string, _, _ int) error {
	return nil
}
