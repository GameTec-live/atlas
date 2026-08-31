package networkmanager

import (
	"context"
	"slices"
	"testing"
)

type runnerCall struct {
	input string
	name  string
	args  []string
}

type fakeRunner struct {
	output string
	calls  []runnerCall
}

func (r *fakeRunner) Run(_ context.Context, input, name string, args ...string) (string, error) {
	r.calls = append(r.calls, runnerCall{input: input, name: name, args: slices.Clone(args)})
	return r.output, nil
}

func TestConnectionsParsesEscapedNmcliFields(t *testing.T) {
	runner := &fakeRunner{output: "uuid-1:Office\\: primary:802-11-wireless:wlan0\n"}
	connections, err := New(runner).Connections(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if len(connections) != 1 || connections[0].Name != "Office: primary" || connections[0].Device != "wlan0" {
		t.Fatalf("unexpected connections: %#v", connections)
	}
}

func TestDevicesIncludesDisconnectedInterfaces(t *testing.T) {
	runner := &fakeRunner{output: "eth0:ethernet:connected:Wired connection 1\nwlan0:wifi:disconnected:\n"}
	devices, err := New(runner).Devices(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if len(devices) != 2 || devices[1].Interface != "wlan0" || devices[1].State != "disconnected" {
		t.Fatalf("unexpected devices: %#v", devices)
	}
}

func TestSetIPSettingsValidatesBeforeCallingNmcli(t *testing.T) {
	runner := &fakeRunner{}
	manager := New(runner)
	err := manager.SetIPSettings(context.Background(), "uuid", IPSettings{
		IPv4: IPFamily{Method: "manual", Addresses: []string{"192.168.1.5/24"}, Gateway: "192.168.1.1", DNS: []string{"1.1.1.1"}},
		IPv6: IPFamily{Method: "auto", Addresses: []string{}, DNS: []string{}},
	})
	if err != nil {
		t.Fatal(err)
	}
	if len(runner.calls) != 2 || !slices.Contains(runner.calls[0].args, "ipv4.method") || runner.calls[1].args[0] != "--wait" {
		t.Fatalf("unexpected nmcli calls: %#v", runner.calls)
	}

	invalidRunner := &fakeRunner{}
	err = New(invalidRunner).SetIPSettings(context.Background(), "uuid", IPSettings{
		IPv4: IPFamily{Method: "manual", Addresses: []string{"not-a-prefix"}},
		IPv6: IPFamily{Method: "disabled"},
	})
	if err == nil || len(invalidRunner.calls) != 0 {
		t.Fatalf("invalid settings should not reach nmcli: error=%v calls=%#v", err, invalidRunner.calls)
	}
}

func TestWifiPasswordUsesStdinNotArguments(t *testing.T) {
	runner := &fakeRunner{}
	password := "correct horse battery staple"
	if err := New(runner).ConnectWifi(context.Background(), WifiRequest{SSID: "Atlas", Password: password}); err != nil {
		t.Fatal(err)
	}
	if len(runner.calls) != 1 || runner.calls[0].input != password+"\n" || slices.Contains(runner.calls[0].args, password) {
		t.Fatalf("password was not isolated from argv: %#v", runner.calls)
	}
}
