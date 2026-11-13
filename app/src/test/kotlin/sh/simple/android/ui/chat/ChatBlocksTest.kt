package sh.simple.android.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.simple.android.model.Message
import sh.simple.android.model.Tool

class ChatBlocksTest {
    private fun tool(id: String, name: String, status: String = "ok", input: String? = null) =
        Message(id, "tool", 0, tool = Tool(name, id, input = input, status = status))

    @Test
    fun userBubbleStripsImageMarkers() {
        val blocks = chatBlocks(listOf(Message("u", "user", 0, text = "[Image #1]What is this?")))
        val user = blocks.single() as ChatBlock.User
        assertEquals("What is this?", user.text)
    }

    @Test
    fun consecutiveToolsFoldIntoOneGroup() {
        val blocks = chatBlocks(
            listOf(
                Message("u", "user", 0, text = "go"),
                tool("a", "Read"),
                tool("b", "Grep"),
                tool("c", "Bash"),
                Message("t", "assistant", 0, text = "done"),
                tool("d", "Edit"),
            ),
        )
        assertEquals(4, blocks.size)
        val first = blocks[1] as ChatBlock.Tools
        assertEquals("Explored 1 file, 1 search, ran 1 command", toolGroupLabel(first.tools))
        assertEquals("Edited 1 file", toolGroupLabel((blocks[3] as ChatBlock.Tools).tools))
    }

    @Test
    fun repeatedEditsToOneFileCountAsOneFile() {
        val tools = listOf(tool("a", "Edit"), tool("b", "Edit"), tool("c", "Read"), tool("d", "Read"))
            .map { toolEntry(it.id, it.tool!!.copy(summary = if (it.tool!!.name == "Edit") "main.go" else it.id)) }
        assertEquals("Explored 2 files, edited 1 file", toolGroupLabel(tools))
    }

    @Test
    fun deniedToolsAreNotCountedAsRun() {
        val tools = listOf(tool("a", "Bash"), tool("b", "Bash"))
            .map { toolEntry(it.id, it.tool!!.copy(status = if (it.id == "a") "denied" else "ok")) }
        assertEquals("Ran 1 command, denied 1 request", toolGroupLabel(tools))
        assertEquals("Denied", tools.first().verb)
    }

    @Test
    fun liveToolIsTheLastRunningOne() {
        val tools = listOf(tool("a", "Read"), tool("b", "Bash", "running")).map { toolEntry(it.id, it.tool!!) }
        assertEquals("b", liveTool(tools)?.id)
    }

    @Test
    fun todoAndCommandInputsAreParsed() {
        val todo = toolEntry("x", Tool("TodoWrite", "", input = """{"todos":[{"content":"Ship","status":"completed"}]}""", status = "ok"))
        assertEquals(listOf(TodoItem("Ship", "completed")), todo.todos)
        val cmd = toolEntry("y", Tool("Bash", "", input = """{"command":"go test ./..."}""", status = "ok"))
        assertEquals("go test ./...", cmd.command)
        assertEquals("go test ./...", cmd.detail)
        assertTrue(toolEntry("z", Tool("Edit", "", input = "not json", status = "ok")).todos.isEmpty())
    }

    @Test
    fun diffLinesDropHeaders() {
        assertEquals(listOf('-' to "a", '+' to "b"), diffLines("---\n+++ \n-a\n+b"))
    }

    @Test
    fun thinkingStopAndWorkedFooter() {
        val msgs = listOf(
            Message("u1", "user", 0, text = "go"),
            Message("r1", "thinking", 1000, text = "plan"),
            Message("a", "tool", 2000, tool = Tool("Read", "a", status = "ok")),
            Message("t1", "assistant", 75_000, text = "done"),
            Message("u2", "user", 80_000, text = "again"),
            Message("e1", "event", 81_000, text = "Stopped"),
        )
        val blocks = chatBlocks(msgs)
        assertEquals(
            listOf("User", "Thinking", "Tools", "Assistant", "User", "Marker"),
            blocks.map { it::class.simpleName },
        )
        assertEquals("Worked 1m · 2 steps", workedPillLabel(turnStepCount(blocks.subList(1, 4)), 75_000L))
        assertEquals("Stopped", (blocks[5] as ChatBlock.Marker).text)
    }

    @Test
    fun noFooterWhileWorkingOrForShortTextTurns() {
        val msgs = listOf(
            Message("u1", "user", 0, text = "go"),
            Message("a", "tool", 30_000, tool = Tool("Read", "a", status = "ok")),
        )
        assertTrue(chatBlocks(msgs, working = true).none { it is ChatBlock.Marker })
        assertEquals(null, workedLabel(4_000))
        assertEquals("Worked for 2h 5m", workedLabel(7_500_000))
    }
}
