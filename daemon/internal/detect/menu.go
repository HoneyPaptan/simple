package detect

import (
	"strconv"
	"strings"

	"sh.simple/simpled/internal/wire"
)

const menuCursor = "❯"

func ParseForKind(kind, text string) *Result {
	if kind == "herdr" {
		if r := parseGateLine(text); r != nil {
			return r
		}
		return parseMenu(strings.TrimSpace(text))
	}
	if KindUsesScreenDetectors(kind) {
		return Parse(text)
	}
	if r := parseGateLine(text); r != nil {
		return r
	}
	r := parseMenu(strings.TrimSpace(text))
	if r == nil || numberedOptions(r.Options) {
		return nil
	}
	return r
}

func numberedOptions(opts []wire.Option) bool {
	for _, o := range opts {
		if len(o.Label) > 1 && o.Label[0] >= '1' && o.Label[0] <= '9' && (o.Label[1] == '.' || o.Label[1] == ')') {
			return true
		}
	}
	return false
}

func parseGateLine(text string) *Result {
	lines := splitLines(text)
	for i := len(lines) - 1; i >= 0; i-- {
		line := strings.TrimSpace(lines[i])
		if line == "" {
			continue
		}
		if !parseContinueLine(line) {
			return nil
		}
		return &Result{Detector: "continue", Title: line, Body: menuBody(lines[:i], line)}
	}
	return nil
}

func parseMenu(text string) *Result {
	lines := splitLines(text)
	cursor := -1
	for i := len(lines) - 1; i >= 0; i-- {
		if strings.HasPrefix(strings.TrimSpace(lines[i]), menuCursor) {
			cursor = i
			break
		}
	}
	if cursor < 0 {
		return nil
	}
	indent := menuIndent(lines[cursor])
	start, end := cursor, cursor
	for start > 0 && isMenuOption(lines[start-1], indent) && !isQuestion(lines[start-1]) {
		start--
	}
	for end+1 < len(lines) && isMenuOption(lines[end+1], indent) {
		end++
	}
	if end == start {
		return nil
	}
	var opts []wire.Option
	for i := start; i <= end; i++ {
		label := strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(lines[i]), menuCursor))
		opts = append(opts, wire.Option{ID: strconv.Itoa(i - start + 1), Label: label})
	}
	title := menuTitle(lines[:start])
	if title == "" {
		return nil
	}
	return &Result{
		Detector: "menu:" + strconv.Itoa(cursor-start+1),
		Title:    title,
		Body:     menuBody(lines[:start], title),
		Options:  opts,
	}
}

func menuIndent(line string) int {
	trimmed := strings.TrimLeft(line, " ")
	return len(line) - len(trimmed)
}

func isMenuOption(line string, indent int) bool {
	trim := strings.TrimSpace(line)
	if trim == "" || strings.HasPrefix(trim, menuCursor) {
		return trim != ""
	}
	return menuIndent(line) >= indent && menuIndent(line) <= indent+len(menuCursor)+2 && hasLetters(trim)
}

func menuTitle(above []string) string {
	for i := len(above) - 1; i >= 0; i-- {
		trim := strings.TrimSpace(above[i])
		if trim == "" {
			continue
		}
		if strings.HasSuffix(trim, "?") {
			return trim
		}
	}
	for i := len(above) - 1; i >= 0; i-- {
		trim := strings.TrimSpace(above[i])
		if strings.Contains(trim, "?") {
			if j := strings.LastIndex(trim, "?"); j > 0 {
				k := strings.LastIndexAny(trim[:j], ".:!") + 1
				return strings.TrimSpace(trim[k : j+1])
			}
		}
	}
	return ""
}

func menuKeys(detector string, option *string) []string {
	cur, err := strconv.Atoi(strings.TrimPrefix(detector, "menu:"))
	if err != nil || option == nil {
		return []string{"enter"}
	}
	want, err := strconv.Atoi(*option)
	if err != nil {
		return []string{"enter"}
	}
	var keys []string
	for ; cur < want; cur++ {
		keys = append(keys, "down")
	}
	for ; cur > want; cur-- {
		keys = append(keys, "up")
	}
	return append(keys, "enter")
}

func menuBody(above []string, title string) *string {
	for i := len(above) - 1; i >= 0; i-- {
		if strings.Count(above[i], "─") >= 10 {
			above = above[i+1:]
			break
		}
	}
	var paras []string
	var cur []string
	flush := func() {
		if len(cur) > 0 {
			p := strings.Join(cur, " ")
			if !strings.Contains(p, title) {
				paras = append(paras, p)
			}
			cur = nil
		}
	}
	for _, line := range above {
		trim := strings.TrimSpace(line)
		if trim == "" || !hasLetters(trim) {
			flush()
			continue
		}
		cur = append(cur, trim)
	}
	flush()
	if len(paras) > 4 {
		paras = paras[len(paras)-4:]
	}
	if len(paras) == 0 {
		return nil
	}
	body := strings.Join(paras, "\n")
	return &body
}

func isQuestion(line string) bool {
	return strings.HasSuffix(strings.TrimSpace(line), "?")
}
