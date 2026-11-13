package hooks

import "testing"

func TestToolCommandReadableOnlyForKnownTools(t *testing.T) {
	cases := []struct{ tool, in, want string }{
		{"Bash", `{"command":"go test ./...","description":"run tests"}`, "go test ./..."},
		{"Write", `{"file_path":"/p/a.txt","content":"hello\n"}`, "/p/a.txt\n\nhello"},
		{"Write", `{"file_path":"/p/a.txt","content":"1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n13\n14"}`, "/p/a.txt\n\n1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n(2 more lines)"},
		{"Edit", `{"file_path":"/p/a.go","old_string":"x := 1","new_string":"x := 2","replace_all":true}`, "/p/a.go (every match)\n\n- x := 1\n+ x := 2"},
		{"WebFetch", `{"url":"https://example.com","prompt":"read"}`, "https://example.com"},
		{"mcp__fs__delete", `{"path":"/p","recursive":true}`, `{"path":"/p","recursive":true}`},
		{"Write", `{"file_path":"/p/a.txt","content":"x","mode":"append"}`, `{"file_path":"/p/a.txt","content":"x","mode":"append"}`},
	}
	for _, c := range cases {
		if got := toolCommand(c.tool, []byte(c.in)); got != c.want {
			t.Errorf("%s %s: got %q want %q", c.tool, c.in, got, c.want)
		}
	}
}
