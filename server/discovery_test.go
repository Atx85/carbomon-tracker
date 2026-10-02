package main

import (
	"net"
	"reflect"
	"testing"
)

func TestDiscoveryAdvertisesOnlyMatchingLANAddresses(t *testing.T) {
	var addresses []net.Addr
	for _, value := range []string{"127.0.0.1/8", "192.168.0.181/24", "10.0.0.2/8", "169.254.1.2/16", "8.8.8.8/24", "fe80::1/64", "fd00::1/64"} {
		ip, network, err := net.ParseCIDR(value)
		if err != nil {
			t.Fatal(err)
		}
		network.IP = ip
		addresses = append(addresses, network)
	}
	for _, test := range []struct {
		bound string
		want  []string
	}{
		{"0.0.0.0", []string{"192.168.0.181", "10.0.0.2", "169.254.1.2"}},
		{"192.168.0.181", []string{"192.168.0.181"}},
		{"127.0.0.1", nil},
		{"192.168.0.99", nil},
		{"fd00::1", nil},
	} {
		t.Run(test.bound, func(t *testing.T) {
			if got := discoveryIPs(net.ParseIP(test.bound), addresses); !reflect.DeepEqual(got, test.want) {
				t.Fatalf("got %v, want %v", got, test.want)
			}
		})
	}
}
