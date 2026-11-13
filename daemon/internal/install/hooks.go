package install

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const hookMarker = "simpled hook"

func InstallHooks(simpledPath string) error {
	home, err := os.UserHomeDir()
	if err != nil {
		return err
	}
	if err := mergeCodexHooks(filepath.Join(home, ".codex", "hooks.json"), simpledPath); err != nil {
		return err
	}
	if err := mergeCursorHooks(filepath.Join(home, ".cursor", "hooks.json"), simpledPath); err != nil {
		return err
	}
	return writeOpenCodePlugin(filepath.Join(home, ".config", "opencode", "plugins", "simple.ts"), simpledPath)
}

func UninstallHooks() error {
	home, err := os.UserHomeDir()
	if err != nil {
		return err
	}
	if err := stripCodexHooks(filepath.Join(home, ".codex", "hooks.json")); err != nil {
		return err
	}
	if err := stripCursorHooks(filepath.Join(home, ".cursor", "hooks.json")); err != nil {
		return err
	}
	return removeOpenCodePlugin(filepath.Join(home, ".config", "opencode", "plugins", "simple.ts"))
}

type hookEvent struct {
	name    string
	timeout int
}

var codexEvents = []hookEvent{
	{"SessionStart", 30},
	{"PermissionRequest", 600},
	{"UserPromptSubmit", 30},
	{"Stop", 30},
	{"SessionEnd", 3},
}

var cursorEvents = []hookEvent{
	{"sessionStart", 30},
	{"beforeShellExecution", 600},
	{"beforeMCPExecution", 600},
	{"beforeSubmitPrompt", 30},
	{"afterAgentResponse", 30},
	{"stop", 30},
	{"sessionEnd", 30},
}

func mergeCodexHooks(path, simpledPath string) error {
	return editHooksFile(path, nil, func(hooks map[string]any) {
		for _, ev := range codexEvents {
			list := withoutOurs(hooks[ev.name], isOursNested)
			list = append(list, map[string]any{
				"matcher": "",
				"hooks": []any{map[string]any{
					"type":    "command",
					"command": hookShell(simpledPath, "codex", ev.name),
					"timeout": ev.timeout,
				}},
			})
			hooks[ev.name] = list
		}
	})
}

func mergeCursorHooks(path, simpledPath string) error {
	return editHooksFile(path, map[string]any{"version": 1}, func(hooks map[string]any) {
		for _, ev := range cursorEvents {
			list := withoutOurs(hooks[ev.name], isOursFlat)
			list = append(list, map[string]any{
				"command": hookShell(simpledPath, "cursor", ev.name),
				"timeout": ev.timeout,
			})
			hooks[ev.name] = list
		}
	})
}

func stripCodexHooks(path string) error { return stripHooksFile(path, isOursNested) }

func stripCursorHooks(path string) error { return stripHooksFile(path, isOursFlat) }

func stripHooksFile(path string, ours func(any) bool) error {
	if _, err := os.Stat(path); os.IsNotExist(err) {
		return nil
	}
	return editHooksFile(path, nil, func(hooks map[string]any) {
		for name, raw := range hooks {
			if _, isList := raw.([]any); !isList {
				continue
			}
			if kept := withoutOurs(raw, ours); len(kept) > 0 {
				hooks[name] = kept
			} else {
				delete(hooks, name)
			}
		}
	})
}

func editHooksFile(path string, fresh map[string]any, edit func(map[string]any)) error {
	root := map[string]any{}
	for k, v := range fresh {
		root[k] = v
	}
	data, err := os.ReadFile(path)
	switch {
	case err == nil:
		if err := json.Unmarshal(data, &root); err != nil {
			return fmt.Errorf("%s is not valid JSON, left unchanged: %w", path, err)
		}
	case !os.IsNotExist(err):
		return err
	}
	hooks, ok := root["hooks"].(map[string]any)
	if root["hooks"] != nil && !ok {
		return fmt.Errorf("%s: hooks is not an object, left unchanged", path)
	}
	if hooks == nil {
		hooks = map[string]any{}
	}
	edit(hooks)
	root["hooks"] = hooks
	return writeJSONOnceBacked(path, data, root)
}

func withoutOurs(raw any, ours func(any) bool) []any {
	list, _ := raw.([]any)
	kept := []any{}
	for _, e := range list {
		if !ours(e) {
			kept = append(kept, e)
		}
	}
	return kept
}

func isOursFlat(e any) bool {
	m, _ := e.(map[string]any)
	cmd, _ := m["command"].(string)
	return isOurCommand(cmd)
}

var ourHookCommand = regexp.MustCompile(`simpled(\.exe)?['"]? hook `)

func isOurCommand(cmd string) bool {
	return ourHookCommand.MatchString(cmd)
}

func isOursNested(e any) bool {
	m, _ := e.(map[string]any)
	inner, _ := m["hooks"].([]any)
	if len(inner) == 0 {
		return false
	}
	for _, h := range inner {
		if !isOursFlat(h) {
			return false
		}
	}
	return true
}

func writeJSONOnceBacked(path string, old []byte, v any) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	if old != nil {
		bak := path + ".bak"
		if _, err := os.Stat(bak); os.IsNotExist(err) {
			if err := os.WriteFile(bak, old, 0o600); err != nil {
				return err
			}
		}
	}
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, append(b, '\n'), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

func writeOpenCodePlugin(path, simpledPath string) error {
	body := fmt.Sprintf(`// simpled hook opencode
export const Simple = async ({ client, directory }) => ({
  "permission.ask": async (input, output) => {
    if (!process.env.HC_TERMINAL_ID) return;
    const proc = Bun.spawn([%q, "hook", "opencode", "permission.ask"], {
      stdin: new Blob([JSON.stringify(input)]),
      stdout: "pipe",
      stderr: "ignore",
    });
    await proc.exited;
    const out = (await new Response(proc.stdout).text()).trim();
    if (!out) return;
    try {
      const parsed = JSON.parse(out);
      if (parsed.status === "allow" || parsed.status === "deny") output.status = parsed.status;
    } catch {}
  },
  event: async ({ event }) => {
    if (!process.env.HC_TERMINAL_ID) return;
    const input = JSON.stringify(event);
    if (event.type === "permission.asked") {
      const proc = Bun.spawn([%q, "hook", "opencode", "permission.asked"], {
        stdin: new Blob([input]),
        stdout: "pipe",
        stderr: "ignore",
      });
      await proc.exited;
      const out = (await new Response(proc.stdout).text()).trim();
      if (!out) return;
      let reply = null;
      try {
        const parsed = JSON.parse(out);
        if (["once", "always", "reject"].includes(parsed.reply)) reply = parsed.reply;
      } catch {}
      if (!reply) return;
      const id = event.properties?.id;
      if (!id) return;
      if (client.permission?.reply) {
        await client.permission.reply({ requestID: id, reply, directory });
        return;
      }
      const sessionID = event.properties?.sessionID;
      if (sessionID && client.postSessionIdPermissionsPermissionId) {
        await client.postSessionIdPermissionsPermissionId({
          path: { id: sessionID, permissionID: id },
          body: { response: reply },
        });
      }
      return;
    }
    const proc = Bun.spawn([%q, "hook", "opencode", event.type], {
      stdin: new Blob([input]),
      stdout: "ignore",
      stderr: "ignore",
    });
    await proc.exited;
  },
});
`, simpledPath, simpledPath, simpledPath)
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	bak := path + ".bak"
	if _, err := os.Stat(path); err == nil {
		if _, err := os.Stat(bak); os.IsNotExist(err) {
			data, _ := os.ReadFile(path)
			_ = os.WriteFile(bak, data, 0o644)
		}
	}
	return os.WriteFile(path, []byte(body), 0o644)
}

func removeOpenCodePlugin(path string) error {
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	if !strings.Contains(string(data), hookMarker) {
		return nil
	}
	return os.Remove(path)
}
