package sh.simple.android.ui.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.simple.android.ui.theme.SimpleTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilitySemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun composerSendButtonLabeledAndEnabled() {
        var text by mutableStateOf("hi")
        composeRule.setContent {
            SimpleTheme {
                Box(Modifier.fillMaxSize()) {
                    ComposerCard(
                        chips = {},
                        text = text,
                        onTextChange = { text = it },
                        modeLabel = "Ask",
                        modelLabel = "Default",
                        sending = false,
                        onPlus = {},
                        onMic = {},
                        onSend = {},
                        onStop = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Send").assertExists().assertIsEnabled()
    }

    @Test
    fun approvalCardActionsPresent() {
        composeRule.setContent {
            SimpleTheme {
                ActionCard(
                    title = "Allow edits",
                    subtitle = "File change",
                    monoBlock = "touch foo",
                    primary = "Allow",
                    secondary = "Deny",
                    onPrimary = {},
                    onSecondary = {},
                )
            }
        }
        composeRule.onNode(hasText("Allow") and hasClickAction()).assertIsDisplayed().assertIsEnabled()
        composeRule.onNode(hasText("Deny") and hasClickAction()).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun dialogTitleReceivesFocus() {
        composeRule.setContent {
            SimpleTheme {
                OverlayRoot {
                    CenterDialog(
                        title = "Rename agent",
                        visible = true,
                        onDismiss = {},
                        body = "Pick a new title",
                        buttons = listOf(DialogButtonSpec("Save") {}),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Rename agent").assertIsDisplayed()
    }

    @Test
    fun labeledIconClickableRequiresDescription() {
        composeRule.setContent {
            SimpleTheme {
                CircleButton(Icons.Copy, onClick = {}, contentDescription = "Copy")
            }
        }
        composeRule.onNode(hasContentDescription("Copy")).assertIsDisplayed()
    }

    @Test
    fun bareIconInClickableFailsWithoutParentLabel() {
        composeRule.setContent {
            SimpleTheme {
                Box(
                    Modifier
                        .testTag("bad")
                        .clickable(role = Role.Button, onClick = {}),
                    propagateMinConstraints = true,
                ) {
                    SimpleIcon(Icons.Copy, contentDescription = null)
                }
            }
        }
        val node = composeRule.onNodeWithTag("bad").fetchSemanticsNode()
        val described = node.config.contains(SemanticsProperties.ContentDescription)
        check(!described)
    }
}
