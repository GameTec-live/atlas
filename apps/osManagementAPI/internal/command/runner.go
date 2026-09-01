package command

import (
	"bytes"
	"context"
	"fmt"
	"os/exec"
	"strings"
)

// Runner keeps host command execution injectable and ensures adapters never
// need a shell. Arguments from API requests are always passed as distinct argv
// values.
type Runner interface {
	Run(ctx context.Context, input string, name string, args ...string) (string, error)
}

type ExecRunner struct{}

func (ExecRunner) Run(ctx context.Context, input string, name string, args ...string) (string, error) {
	cmd := exec.CommandContext(ctx, name, args...)
	if input != "" {
		cmd.Stdin = strings.NewReader(input)
	}
	var output bytes.Buffer
	cmd.Stdout = &output
	cmd.Stderr = &output
	if err := cmd.Run(); err != nil {
		message := strings.TrimSpace(output.String())
		if message == "" {
			return "", fmt.Errorf("%s: %w", name, err)
		}
		return "", fmt.Errorf("%s: %w: %s", name, err, message)
	}
	return strings.TrimSpace(output.String()), nil
}
