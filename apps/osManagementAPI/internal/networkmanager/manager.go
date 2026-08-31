package networkmanager

import (
	"context"
	"fmt"
	"net"
	"net/netip"
	"slices"
	"strings"

	"github.com/GameTec-live/atlas/apps/osManagementAPI/internal/command"
)

const nmcli = "/usr/bin/nmcli"

type Connection struct {
	UUID   string `json:"uuid"`
	Name   string `json:"name"`
	Type   string `json:"type"`
	Device string `json:"device,omitempty"`
}

type Device struct {
	Interface  string `json:"interface"`
	Type       string `json:"type"`
	State      string `json:"state"`
	Connection string `json:"connection,omitempty"`
}

type AccessPoint struct {
	Active    bool   `json:"active"`
	SSID      string `json:"ssid"`
	BSSID     string `json:"bssid"`
	Signal    int    `json:"signal"`
	Security  string `json:"security,omitempty"`
	Frequency int    `json:"frequency"`
}

type IPFamily struct {
	Method    string   `json:"method"`
	Addresses []string `json:"addresses"`
	Gateway   string   `json:"gateway,omitempty"`
	DNS       []string `json:"dns"`
}

type IPSettings struct {
	IPv4 IPFamily `json:"ipv4"`
	IPv6 IPFamily `json:"ipv6"`
}

type WifiRequest struct {
	SSID     string `json:"ssid"`
	Password string `json:"password,omitempty"`
	Device   string `json:"device,omitempty"`
	Hidden   bool   `json:"hidden,omitempty"`
}

type Manager struct {
	runner command.Runner
}

func New(runner command.Runner) *Manager {
	return &Manager{runner: runner}
}

func (m *Manager) Connections(ctx context.Context) ([]Connection, error) {
	output, err := m.runner.Run(ctx, "", nmcli, "--terse", "--escape", "yes", "--fields", "UUID,NAME,TYPE,DEVICE", "connection", "show")
	if err != nil {
		return nil, err
	}
	connections := []Connection{}
	for line := range strings.Lines(output) {
		fields, err := splitEscaped(strings.TrimSpace(line), ':', 4)
		if err != nil {
			return nil, err
		}
		connections = append(connections, Connection{UUID: fields[0], Name: fields[1], Type: fields[2], Device: fields[3]})
	}
	return connections, nil
}

func (m *Manager) Devices(ctx context.Context) ([]Device, error) {
	output, err := m.runner.Run(ctx, "", nmcli, "--terse", "--escape", "yes", "--fields", "DEVICE,TYPE,STATE,CONNECTION", "device", "status")
	if err != nil {
		return nil, err
	}
	devices := []Device{}
	for line := range strings.Lines(output) {
		fields, err := splitEscaped(strings.TrimSpace(line), ':', 4)
		if err != nil {
			return nil, err
		}
		devices = append(devices, Device{Interface: fields[0], Type: fields[1], State: fields[2], Connection: fields[3]})
	}
	return devices, nil
}

func (m *Manager) IPSettings(ctx context.Context, uuid string) (IPSettings, error) {
	if err := validateID(uuid); err != nil {
		return IPSettings{}, err
	}
	output, err := m.runner.Run(ctx, "", nmcli, "--terse", "--escape", "yes", "--fields", "ipv4.method,ipv4.addresses,ipv4.gateway,ipv4.dns,ipv6.method,ipv6.addresses,ipv6.gateway,ipv6.dns", "connection", "show", "uuid", uuid)
	if err != nil {
		return IPSettings{}, err
	}
	values := map[string]string{}
	for line := range strings.Lines(output) {
		fields, splitErr := splitEscaped(strings.TrimSpace(line), ':', 2)
		if splitErr != nil {
			return IPSettings{}, splitErr
		}
		values[fields[0]] = fields[1]
	}
	return IPSettings{
		IPv4: IPFamily{Method: values["ipv4.method"], Addresses: splitList(values["ipv4.addresses"]), Gateway: values["ipv4.gateway"], DNS: splitList(values["ipv4.dns"])},
		IPv6: IPFamily{Method: values["ipv6.method"], Addresses: splitList(values["ipv6.addresses"]), Gateway: values["ipv6.gateway"], DNS: splitList(values["ipv6.dns"])},
	}, nil
}

func (m *Manager) SetIPSettings(ctx context.Context, uuid string, settings IPSettings) error {
	if err := validateID(uuid); err != nil {
		return err
	}
	if err := validateFamily(4, settings.IPv4); err != nil {
		return fmt.Errorf("ipv4: %w", err)
	}
	if err := validateFamily(6, settings.IPv6); err != nil {
		return fmt.Errorf("ipv6: %w", err)
	}
	args := []string{"connection", "modify", "uuid", uuid}
	args = appendFamily(args, "ipv4", settings.IPv4)
	args = appendFamily(args, "ipv6", settings.IPv6)
	if _, err := m.runner.Run(ctx, "", nmcli, args...); err != nil {
		return err
	}
	_, err := m.runner.Run(ctx, "", nmcli, "--wait", "30", "connection", "up", "uuid", uuid)
	return err
}

func (m *Manager) Wifi(ctx context.Context, device string) ([]AccessPoint, error) {
	args := []string{"--terse", "--escape", "yes", "--fields", "IN-USE,SSID,BSSID,SIGNAL,SECURITY,FREQ", "device", "wifi", "list", "--rescan", "yes"}
	if device != "" {
		if err := validateID(device); err != nil {
			return nil, err
		}
		args = append(args, "ifname", device)
	}
	output, err := m.runner.Run(ctx, "", nmcli, args...)
	if err != nil {
		return nil, err
	}
	accessPoints := []AccessPoint{}
	for line := range strings.Lines(output) {
		fields, splitErr := splitEscaped(strings.TrimSpace(line), ':', 6)
		if splitErr != nil {
			return nil, splitErr
		}
		var signal, frequency int
		if _, err := fmt.Sscanf(fields[3], "%d", &signal); err != nil {
			return nil, fmt.Errorf("invalid Wi-Fi signal: %q", fields[3])
		}
		if _, err := fmt.Sscanf(fields[5], "%d", &frequency); err != nil {
			return nil, fmt.Errorf("invalid Wi-Fi frequency: %q", fields[5])
		}
		accessPoints = append(accessPoints, AccessPoint{Active: fields[0] == "yes", SSID: fields[1], BSSID: fields[2], Signal: signal, Security: fields[4], Frequency: frequency})
	}
	return accessPoints, nil
}

func (m *Manager) ConnectWifi(ctx context.Context, request WifiRequest) error {
	if request.SSID == "" || len(request.SSID) > 32 {
		return fmt.Errorf("ssid must contain between 1 and 32 bytes")
	}
	if strings.ContainsAny(request.SSID, "\x00\r\n") || strings.ContainsAny(request.Device, "\x00\r\n") {
		return fmt.Errorf("ssid and device must not contain control characters")
	}
	args := []string{"--wait", "30"}
	input := ""
	if request.Password != "" {
		args = append(args, "--ask")
		input = request.Password + "\n"
	}
	args = append(args, "device", "wifi", "connect", request.SSID)
	if request.Device != "" {
		args = append(args, "ifname", request.Device)
	}
	if request.Hidden {
		args = append(args, "hidden", "yes")
	}
	_, err := m.runner.Run(ctx, input, nmcli, args...)
	return err
}

func (m *Manager) Disconnect(ctx context.Context, uuid string) error {
	if err := validateID(uuid); err != nil {
		return err
	}
	_, err := m.runner.Run(ctx, "", nmcli, "connection", "down", "uuid", uuid)
	return err
}

func (m *Manager) Forget(ctx context.Context, uuid string) error {
	if err := validateID(uuid); err != nil {
		return err
	}
	_, err := m.runner.Run(ctx, "", nmcli, "connection", "delete", "uuid", uuid)
	return err
}

func validateID(value string) error {
	if value == "" || len(value) > 128 || strings.ContainsAny(value, "\x00\r\n") {
		return fmt.Errorf("invalid identifier")
	}
	return nil
}

func validateFamily(version int, family IPFamily) error {
	allowed := []string{"auto", "manual", "disabled"}
	if !slices.Contains(allowed, family.Method) {
		return fmt.Errorf("method must be auto, manual, or disabled")
	}
	if family.Method == "manual" && len(family.Addresses) == 0 {
		return fmt.Errorf("manual method requires at least one address")
	}
	for _, address := range family.Addresses {
		prefix, err := netip.ParsePrefix(address)
		if err != nil || (version == 4) != prefix.Addr().Is4() {
			return fmt.Errorf("invalid IPv%d CIDR address %q", version, address)
		}
	}
	for label, addresses := range map[string][]string{"gateway": {family.Gateway}, "DNS server": family.DNS} {
		for _, address := range addresses {
			if address == "" {
				continue
			}
			parsed := net.ParseIP(address)
			if parsed == nil || (version == 4) != (parsed.To4() != nil) {
				return fmt.Errorf("invalid IPv%d %s %q", version, label, address)
			}
		}
	}
	return nil
}

func appendFamily(args []string, prefix string, family IPFamily) []string {
	return append(args,
		prefix+".method", family.Method,
		prefix+".addresses", strings.Join(family.Addresses, ","),
		prefix+".gateway", family.Gateway,
		prefix+".dns", strings.Join(family.DNS, ","),
	)
}

func splitList(value string) []string {
	if value == "" {
		return []string{}
	}
	items := strings.Split(value, ",")
	for index := range items {
		items[index] = strings.TrimSpace(items[index])
	}
	return items
}

func splitEscaped(value string, separator byte, expected int) ([]string, error) {
	result := make([]string, 0, expected)
	var field strings.Builder
	escaped := false
	for index := 0; index < len(value); index++ {
		character := value[index]
		if escaped {
			field.WriteByte(character)
			escaped = false
			continue
		}
		if character == '\\' {
			escaped = true
			continue
		}
		if character == separator && len(result) < expected-1 {
			result = append(result, field.String())
			field.Reset()
			continue
		}
		field.WriteByte(character)
	}
	if escaped {
		return nil, fmt.Errorf("invalid trailing escape in nmcli output")
	}
	result = append(result, field.String())
	if len(result) != expected {
		return nil, fmt.Errorf("invalid nmcli output: expected %d fields, got %d", expected, len(result))
	}
	return result, nil
}
