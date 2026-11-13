package detect

import (
	"crypto/sha256"
	"encoding/hex"
	"strings"

	"sh.simple/simpled/internal/wire"
)

type Result struct {
	Detector string
	Title    string
	Body     *string
	Options  []wire.Option
	Link     *string
}

func Fingerprint(r *Result) string {
	if r == nil {
		return ""
	}
	h := sha256.New()
	h.Write([]byte(r.Detector))
	h.Write([]byte{0})
	h.Write([]byte(r.Title))
	if r.Body != nil {
		h.Write([]byte{0})
		h.Write([]byte(*r.Body))
	}
	for _, o := range r.Options {
		h.Write([]byte{0})
		h.Write([]byte(o.ID))
		h.Write([]byte(o.Label))
	}
	if r.Link != nil {
		h.Write([]byte{0})
		h.Write([]byte(*r.Link))
	}
	return hex.EncodeToString(h.Sum(nil))
}

func FingerprintForItem(item wire.Item) string {
	if item.Detector == nil {
		return ""
	}
	return Fingerprint(&Result{
		Detector: *item.Detector,
		Title:    item.Title,
		Body:     item.Body,
		Options:  item.Options,
		Link:     item.Link,
	})
}

func ToItem(agentID, itemID string, createdMs int64, r *Result) wire.Item {
	d := r.Detector
	return wire.Item{
		ID:        itemID,
		Agent:     agentID,
		Kind:      "prompt",
		Title:     r.Title,
		Body:      r.Body,
		Options:   r.Options,
		Detector:  &d,
		Link:      r.Link,
		CreatedMs: createdMs,
	}
}

func KeysForAnswer(item wire.Item, decision string, option, text *string) []string {
	if item.Detector == nil {
		return nil
	}
	if strings.HasPrefix(*item.Detector, "menu:") {
		return menuKeys(*item.Detector, option)
	}
	switch *item.Detector {
	case "choice":
		if option != nil && *option != "" {
			if len(*option) == 1 && *option >= "1" && *option <= "9" {
				return []string{*option, "enter"}
			}
		}
		if decision == "allow" && len(item.Options) > 0 {
			return []string{"1", "enter"}
		}
		return []string{"enter"}
	case "yesno":
		if strings.EqualFold(decision, "deny") {
			return []string{"n", "enter"}
		}
		return []string{"y", "enter"}
	case "continue", "error":
		return []string{"enter"}
	case "input":
		if text != nil && *text != "" {
			return []string{*text, "enter"}
		}
		return []string{"enter"}
	case "link":
		return []string{"enter"}
	default:
		return []string{"enter"}
	}
}
