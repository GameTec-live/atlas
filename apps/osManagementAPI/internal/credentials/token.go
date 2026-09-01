package credentials

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/ownership"
)

func EnsureToken(path string, uid, gid int) (string, error) {
	data, err := os.ReadFile(path)
	if err == nil {
		token := strings.TrimSpace(string(data))
		if token == "" {
			return "", fmt.Errorf("management token is empty")
		}
		return token, nil
	}
	if !errors.Is(err, os.ErrNotExist) {
		return "", fmt.Errorf("read management token: %w", err)
	}

	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return "", fmt.Errorf("create token directory: %w", err)
	}
	if err := os.Chmod(filepath.Dir(path), 0o700); err != nil {
		return "", fmt.Errorf("set token directory mode: %w", err)
	}
	if err := ownership.Set(filepath.Dir(path), uid, gid); err != nil {
		return "", fmt.Errorf("set token directory ownership: %w", err)
	}
	random := make([]byte, 32)
	if _, err := rand.Read(random); err != nil {
		return "", fmt.Errorf("generate management token: %w", err)
	}
	token := hex.EncodeToString(random)
	temporary, err := os.CreateTemp(filepath.Dir(path), ".management-token.*")
	if err != nil {
		return "", fmt.Errorf("create management token: %w", err)
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if err := temporary.Chmod(0o640); err != nil {
		temporary.Close()
		return "", err
	}
	if _, err := temporary.WriteString(token + "\n"); err != nil {
		temporary.Close()
		return "", err
	}
	if err := temporary.Sync(); err != nil {
		temporary.Close()
		return "", err
	}
	if err := temporary.Close(); err != nil {
		return "", err
	}
	if err := ownership.Set(temporaryPath, uid, gid); err != nil {
		return "", fmt.Errorf("set management token ownership: %w", err)
	}
	if err := os.Rename(temporaryPath, path); err != nil {
		if errors.Is(err, os.ErrExist) {
			return EnsureToken(path, uid, gid)
		}
		return "", fmt.Errorf("publish management token: %w", err)
	}
	return token, nil
}
