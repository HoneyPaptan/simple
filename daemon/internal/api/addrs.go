package api

import (
	"net"
	"strings"
)

func tailnetAddrs() ([]net.IP, error) {
	ifaces, err := net.Interfaces()
	if err != nil {
		return nil, err
	}
	v4Net := net.IPNet{IP: net.IPv4(100, 64, 0, 0), Mask: net.CIDRMask(10, 32)}
	var out []net.IP
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, a := range addrs {
			var ip net.IP
			switch v := a.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip == nil || ip.IsLoopback() {
				continue
			}
			if ip4 := ip.To4(); ip4 != nil {
				if v4Net.Contains(ip4) {
					out = append(out, ip4)
				}
				continue
			}
			if len(ip) == net.IPv6len && ip[0] == 0xfd && ip[1] == 0x7a && ip[2] == 0x11 && ip[3] == 0x5c {
				out = append(out, ip)
			}
		}
	}
	return out, nil
}

func listenAddrs(port int, lan bool) ([]string, error) {
	var hosts []string
	hosts = append(hosts, "127.0.0.1")
	tail, _ := tailnetAddrs()
	for _, ip := range tail {
		hosts = append(hosts, ip.String())
	}
	if lan {
		v4Net := net.IPNet{IP: net.IPv4(100, 64, 0, 0), Mask: net.CIDRMask(10, 32)}
		ifaces, _ := net.Interfaces()
		for _, iface := range ifaces {
			if iface.Flags&net.FlagUp == 0 {
				continue
			}
			addrs, _ := iface.Addrs()
			for _, a := range addrs {
				var ip net.IP
				switch v := a.(type) {
				case *net.IPNet:
					ip = v.IP
				case *net.IPAddr:
					ip = v.IP
				}
				if ip == nil || ip.IsLoopback() {
					continue
				}
				if ip4 := ip.To4(); ip4 != nil {
					if !v4Net.Contains(ip4) {
						hosts = append(hosts, ip4.String())
					}
				}
			}
		}
	}
	seen := map[string]bool{}
	var out []string
	for _, h := range hosts {
		k := h
		if seen[k] {
			continue
		}
		seen[k] = true
		out = append(out, net.JoinHostPort(h, itoa(port)))
	}
	return out, nil
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	var b [12]byte
	i := len(b)
	for n > 0 {
		i--
		b[i] = byte('0' + n%10)
		n /= 10
	}
	return string(b[i:])
}

func PairAddrs(port int, lan bool) ([]string, error) {
	all, err := listenAddrs(port, lan)
	if err != nil {
		return nil, err
	}
	var v4, v6 []string
	for _, a := range all {
		host, _, _ := net.SplitHostPort(a)
		switch {
		case host == "127.0.0.1":
		case strings.Contains(host, ":"):
			v6 = append(v6, a)
		default:
			v4 = append(v4, a)
		}
	}
	if len(v4) > 0 {
		return v4, nil
	}
	return v6, nil
}
