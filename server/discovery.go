package main

import (
	"fmt"
	"net"
	"os"
	"strings"
	"unicode"

	"github.com/libp2p/zeroconf/v2"
)

const catalogServiceType = "_carbomon._tcp"

// Only advertise reachable LAN IPv4 addresses on the HTTP listener's interfaces.
// Manual URLs remain available for IPv6-only or multicast-restricted networks.
func discoveryIPs(bound net.IP, addresses []net.Addr) []string {
	var ips []string
	for _, address := range addresses {
		ip, _, err := net.ParseCIDR(address.String())
		if err != nil || ip.To4() == nil || ip.IsLoopback() || !(ip.IsPrivate() || ip.IsLinkLocalUnicast()) {
			continue
		}
		if !bound.IsUnspecified() && !bound.Equal(ip) {
			continue
		}
		ips = append(ips, ip.String())
	}
	return ips
}

func advertiseCatalog(address *net.TCPAddr, serverID string, peers peerConfig) (*zeroconf.Server, error) {
	interfaces, err := net.Interfaces()
	if err != nil {
		return nil, err
	}
	var selected []net.Interface
	var ips []string
	for _, iface := range interfaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagMulticast == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		addresses, err := iface.Addrs()
		if err != nil {
			continue
		}
		local := discoveryIPs(address.IP, addresses)
		if len(local) > 0 {
			selected = append(selected, iface)
			ips = append(ips, local...)
		}
	}
	if len(selected) == 0 {
		return nil, fmt.Errorf("no LAN IPv4 interface matches the listening address")
	}
	host, _ := os.Hostname()
	host = strings.Map(func(r rune) rune {
		if r < 128 && (unicode.IsLetter(r) || unicode.IsDigit(r) || r == '-') {
			return r
		}
		return '-'
	}, host)
	if len(host) > 30 {
		host = host[:30]
	}
	shortID := strings.TrimPrefix(serverID, "catalog-")
	if len(shortID) > 8 {
		shortID = shortID[:8]
	}
	instance := fmt.Sprintf("CarboMon %s (%s)", host, shortID)
	text := []string{"version=1", "id=" + serverID}
	if peers.Enabled {
		text = append(text, "peers=1")
	}
	if peers.Key != "" {
		text = append(text, "peer-key=1")
	}
	return zeroconf.RegisterProxy(instance, catalogServiceType, "local.", address.Port,
		"carbomon-"+serverID+".local.", ips, text, selected, zeroconf.TTL(60))
}
