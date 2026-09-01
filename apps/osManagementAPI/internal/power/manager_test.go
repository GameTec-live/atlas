package power

import (
	"context"
	"slices"
	"testing"
)

type fakeRunner struct {
	name string
	args []string
}

func (r *fakeRunner) Run(_ context.Context, _ string, name string, args ...string) (string, error) {
	r.name = name
	r.args = slices.Clone(args)
	return "", nil
}

func TestTrybootUsesFirmwareRebootArgument(t *testing.T) {
	runner := &fakeRunner{}
	if err := New(runner).RebootTryboot(context.Background()); err != nil {
		t.Fatal(err)
	}
	if runner.name != "/usr/sbin/reboot" || !slices.Equal(runner.args, []string{"0 tryboot"}) {
		t.Fatalf("unexpected tryboot command: %s %#v", runner.name, runner.args)
	}
}
