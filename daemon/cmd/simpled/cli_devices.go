package main

import (
	"fmt"
	"os"
	"sort"
	"text/tabwriter"
	"time"

	"sh.simple/simpled/internal/wire"
)

func relativeLastSeen(ms int64) string {
	if ms == 0 {
		return "never"
	}
	d := time.Since(time.UnixMilli(ms))
	sec := int(d.Round(time.Second) / time.Second)
	if sec < 60 {
		if sec <= 1 {
			return "1 sec ago"
		}
		return fmt.Sprintf("%d sec ago", sec)
	}
	min := sec / 60
	if min < 60 {
		if min == 1 {
			return "1 min ago"
		}
		return fmt.Sprintf("%d min ago", min)
	}
	hr := min / 60
	if hr < 48 {
		if hr == 1 {
			return "1 hr ago"
		}
		return fmt.Sprintf("%d hr ago", hr)
	}
	days := hr / 24
	if days == 1 {
		return "1 day ago"
	}
	return fmt.Sprintf("%d days ago", days)
}

func printDevicesTable(views []wire.DeviceView) {
	sort.Slice(views, func(i, j int) bool {
		return views[i].Created > views[j].Created
	})
	w := tabwriter.NewWriter(os.Stdout, 0, 0, 3, ' ', 0)
	fmt.Fprintln(w, "ID\tNAME\tPAIRED\tLAST SEEN")
	for _, d := range views {
		paired := time.UnixMilli(d.Created).Format("Jan 2, 2006 3:04 PM")
		fmt.Fprintf(w, "%s\t%s\t%s\t%s\n", d.ID, d.Name, paired, relativeLastSeen(d.LastSeen))
	}
	w.Flush()
	if len(views) == 0 {
		return
	}
}
