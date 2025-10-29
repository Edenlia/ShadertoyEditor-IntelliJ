import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * HelloWorldAction
 *
 * @author butterfly
 * @date 2023-11-16
 */
class HelloWorldAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        Notifications.Bus.notify(
            Notification("Print", "", "Hello, World!", NotificationType.INFORMATION),
            e.project
        )
    }

}
